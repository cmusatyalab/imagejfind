/*
 *  ImageJFind
 *  A Diamond application for interoperating with ImageJ
 *  Version 1
 *
 *  Copyright (c) 2006-2010 Carnegie Mellon University
 *  All Rights Reserved.
 *
 *  This software is distributed under the terms of the Eclipse Public
 *  License, Version 1.0 which can be found in the file named LICENSE.
 *  ANY USE, REPRODUCTION OR DISTRIBUTION OF THIS SOFTWARE CONSTITUTES
 *  RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT
 */

#define _GNU_SOURCE

#include "lib_filter.h"

#include <arpa/inet.h>
#include <dirent.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <unistd.h>
#include <assert.h>
#include <glib.h>
#include "quick_tar.h"

#include <stdint.h>
#include "imagej-bin.h"
#include "ijloader-bin.h"
#include "diamond_filter-bin.h"

#define IMAGEJ_FILE "ImageJ.zip"
#define DIAMOND_FILTER_FILE "diamond_filter.jar"
#define IJLOADER_FILE "ijloader.jar"

struct filter_instance {
   GPid ij_pid;
   int ij_to_fd;
   int ij_from_fd;
   FILE *ij_to_file;
   FILE *ij_from_file;
   char *macro_name;
   char *dirname;
};

static void transmit_image(lf_obj_handle_t ohandle, FILE *fp)
{
   unsigned char *obj_data;
   size_t data_len;

   lf_next_block(ohandle, INT_MAX, &data_len, &obj_data);

   printf("Sending %d byte image...\n", data_len);
   int net_img_size = htonl(data_len);

   fwrite(&net_img_size, sizeof(net_img_size), 1, fp);
   fwrite(obj_data, data_len, 1, fp);
}

static void transmit_macro(int macro_len, char *macro, FILE *fp)
{
   int net_macro_len = htonl(macro_len);

   printf("Sending macro name...\n");
   fwrite(&net_macro_len, sizeof(net_macro_len), 1,  fp);
   fwrite(macro, macro_len, 1, fp);
}

static double process_attrs_and_get_result(FILE *fp, lf_obj_handle_t ohandle)
{
  char *lineptr = NULL;

  int begun = 0;
  int len;
  size_t n;

  while (1) {
    getline(&lineptr, &n, fp);
    printf("getline says: %s\n", lineptr);

    if (strcmp(lineptr, "BEGIN\n") == 0 && !begun) {
      begun = 1;
    } else if (strcmp(lineptr, "ATTR\n") == 0) {
      char *attr_name;
      char *attr_val;

      fscanf(fp, "K\n%d\n", &len);
      attr_name = malloc(len + 1);
      fread(attr_name, len + 1, 1, fp);
      attr_name[len] = '\0';

      fscanf(fp, "V\n%d\n", &len);
      attr_val = malloc(len + 1);
      fread(attr_val, len + 1, 1, fp);
      attr_val[len] = '\0';

      printf("%s -> %s\n", attr_name, attr_val);
      lf_write_attr(ohandle, attr_name, len + 1, (unsigned char *) attr_val);
      free(attr_name);
      free(attr_val);
    } else if (strcmp(lineptr, "RESULT\n") == 0) {
      char *result_str;
      double result;

      fscanf(fp, "%d\n", &len);
      result_str = malloc(len + 1);
      fread(result_str, len + 1, 1, fp);
      result_str[len] = '\0';

      result = strtod(result_str, NULL);
      printf("result: %g\n", result);

      free(result_str);
      free(lineptr);

      return result;
    } else {
      printf("BAD LINE %s", lineptr);
      free(lineptr);
      return -1;
    }

    free(lineptr);
    lineptr = NULL;
  }
}

static void unblock_signals(gpointer user_data) {
  sigset_t set;
  g_assert(sigemptyset(&set) == 0);
  g_assert(pthread_sigmask(SIG_SETMASK, &set, NULL) == 0);
}

int f_init_imagej_exec (int num_arg, char **args, int bloblen,
                        void *blob_data, const char *filter_name,
                        void **filter_args)
{
   struct filter_instance *inst =
     (struct filter_instance *)malloc(sizeof(struct filter_instance));

   char temp_dir[] = P_tmpdir "/imagejfindXXXXXX";
   if (mkdtemp(temp_dir) == NULL) {
     perror("Could not create temporary directory");
     exit(0);
   }

   if (chdir(temp_dir) < 0) {
     perror("Could not enter ImageJ directory");
     exit(0);
   }

   inst->dirname = strdup(temp_dir);

   // write ImageJ
   g_assert(g_file_set_contents(IMAGEJ_FILE,
				(const gchar *) imagej_bin.data,
				imagej_bin.len, NULL));

   // create imagej environment
   char *spawn_args[] = { "unzip", IMAGEJ_FILE, NULL };
   g_assert(g_spawn_sync(NULL, spawn_args, NULL,
			 G_SPAWN_SEARCH_PATH | G_SPAWN_STDOUT_TO_DEV_NULL |
			 G_SPAWN_STDERR_TO_DEV_NULL,
			 NULL, NULL, NULL, NULL, NULL, NULL));

   g_assert(chdir("ImageJ") == 0);

   // write ijloader
   g_assert(g_file_set_contents(IJLOADER_FILE,
				(const gchar *) ijloader_bin.data,
				ijloader_bin.len, NULL));

   // write diamond_filter
   g_assert(chdir("plugins") == 0);
   g_assert(g_file_set_contents(DIAMOND_FILTER_FILE,
				(const gchar *) diamond_filter_bin.data,
				diamond_filter_bin.len, NULL));

   // write user blob
   g_assert(mkdir("Diamond", 0700) == 0);
   g_assert(untar_blob("Diamond", bloblen, (char *)blob_data) == 0);

   // write it again to the macros directory
   g_assert(chdir("..") == 0);
   g_assert(untar_blob("macros", bloblen, (char *)blob_data) == 0);

   // go!
   setenv("DISPLAY", "localhost:100", 1);
   char *ij_args[] = { "/usr/bin/java", "-Djava.awt.headless=true", "-server",
		       "-cp", "ij.jar:ijloader.jar:.",
		       "ijloader.IJLoader", NULL };

   GError *err = NULL;
   g_spawn_async_with_pipes(NULL,
			    ij_args,
			    NULL, 0,
			    unblock_signals, NULL,
			    &inst->ij_pid,
			    &inst->ij_to_fd,
			    &inst->ij_from_fd,
			    NULL,
			    &err);
   if (err != NULL) {
     fprintf (stderr, "Unable to spawn: %s\n", err->message);
     abort();
   }


   inst->ij_to_file = fdopen(inst->ij_to_fd, "w");
   inst->ij_from_file = fdopen(inst->ij_from_fd, "r");
   inst->macro_name = args[0];
   *filter_args = inst;

   return 0;
}

int f_eval_imagej_exec (lf_obj_handle_t ohandle, void *filter_args)
{
   struct filter_instance *inst = (struct filter_instance *)filter_args;

   printf("Executing search...\n");

   transmit_image(ohandle, inst->ij_to_file);
   transmit_macro(strlen(inst->macro_name), inst->macro_name, inst->ij_to_file);
   fflush(inst->ij_to_file);
   printf("New image + macro sent...\n");
   fflush(stdout);

   double result = process_attrs_and_get_result(inst->ij_from_file, ohandle);
   lf_write_attr(ohandle, "_matlab_ans.double", sizeof(double), (unsigned char *)&result);

   int int_result = (int) result;
   printf("int_result: %d\n", int_result);

   return int_result;
}

int f_fini_imagej_exec (void *filter_args)
{
   struct filter_instance *inst = (struct filter_instance *)filter_args;

   int status;
   kill(inst->ij_pid, SIGKILL);
   g_spawn_close_pid(inst->ij_pid);

   fclose(inst->ij_to_file);
   fclose(inst->ij_from_file);
   close(inst->ij_to_fd);
   close(inst->ij_from_fd);
   free(inst->macro_name);

   char *rm_args[] = { "rm", "-rf", inst->dirname, NULL };
   g_assert(g_spawn_sync(NULL, rm_args, NULL,
			 G_SPAWN_SEARCH_PATH | G_SPAWN_STDOUT_TO_DEV_NULL |
			 G_SPAWN_STDERR_TO_DEV_NULL,
			 NULL, NULL, NULL, NULL, NULL, NULL));

   free(inst->dirname);

   free(inst);

   return 0;
}

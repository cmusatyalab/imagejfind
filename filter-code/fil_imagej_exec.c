/*
 *  ImageJFind
 *  A Diamond application for interoperating with ImageJ
 *  Version 1
 *
 *  Copyright (c) 2006-2011 Carnegie Mellon University
 *  All Rights Reserved.
 *
 *  This software is distributed under the terms of the Eclipse Public
 *  License, Version 1.0 which can be found in the file named LICENSE.
 *  ANY USE, REPRODUCTION OR DISTRIBUTION OF THIS SOFTWARE CONSTITUTES
 *  RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT
 */

#define _GNU_SOURCE

#include "lib_filter.h"

#include <pthread.h>
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
#include <stdbool.h>
#include <errno.h>
#include "imagej-bin.h"
#include "ijloader-bin.h"
#include "diamond_filter-bin.h"

#define IMAGEJ_FILE "ImageJ.zip"
#define DIAMOND_FILTER_FILE "diamond_filter.jar"
#define IJLOADER_FILE "ijloader.jar"

struct filter_instance {
   FILE *ij_to_file;
   FILE *ij_from_file;
   char *macro_name;
};

static void transmit_image(lf_obj_handle_t ohandle, FILE *fp)
{
   const void *obj_data;
   size_t data_len;

   lf_ref_attr(ohandle, "", &data_len, &obj_data);

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

static int signal_pipe;
static pthread_mutex_t signal_pipe_mutex = PTHREAD_MUTEX_INITIALIZER;

// used in the child of the fork to ignore sigusr1 so that the xserver
// will send us sigusr1 when it is ready (per xinit.c)
static void ignore_sigusr1(gpointer user_data)
{
  struct sigaction action;
  memset(&action, 0, sizeof action);
  action.sa_handler = SIG_IGN;

  sigaction(SIGUSR1, &action, NULL);
}

static void sigchld(int sig)
{
  pthread_mutex_lock(&signal_pipe_mutex);
  int fd = signal_pipe;
  pthread_mutex_unlock(&signal_pipe_mutex);

  // FAIL!
  int status;
  while (true) {
    status = write(fd, "f", 1);
    if (status == -1 && errno != EINTR) {
      perror("write in sigchld");
      abort();
    } else if (status == 1) {
      break;
    }
  }
  wait(&status);
}

static void sigusr1(int sig)
{
  pthread_mutex_lock(&signal_pipe_mutex);
  int fd = signal_pipe;
  pthread_mutex_unlock(&signal_pipe_mutex);

  // WIN!
  int status;
  while (true) {
    status = write(fd, "w", 1);
    if (status == -1 && errno != EINTR) {
      perror("write in sigusr1");
      abort();
    } else if (status == 1) {
      break;
    }
  }
}


static int start_x_server(void)
{
  // create self-pipe
  int pipefd[2];
  pipe(pipefd);

  pthread_mutex_lock(&signal_pipe_mutex);
  signal_pipe = pipefd[1];
  pthread_mutex_unlock(&signal_pipe_mutex);

  // register signal handlers
  struct sigaction action;
  memset(&action, 0, sizeof action);

  action.sa_handler = sigchld;
  sigaction(SIGCHLD, &action, NULL);

  action.sa_handler = sigusr1;
  sigaction(SIGUSR1, &action, NULL);

  int i;
  for (i = 0; i < 10000; i++) {
    printf("Trying DISPLAY :%d\n", i);

    char *display_str = g_strdup_printf(":%d", i);
    char *args[] = { "/usr/bin/Xvfb", display_str, "-terminate", NULL };
    g_spawn_async(NULL, args, NULL,
		  G_SPAWN_DO_NOT_REAP_CHILD,
		  ignore_sigusr1, NULL, NULL, NULL);
    g_free(display_str);

    char result;
    int status;
    while (true) {
      status = read(pipefd[0], &result, 1);
      fprintf(stderr, "result: %d\n", (int) result);
      if (status == -1 && errno != EINTR) {
	perror("read from pipe");
	abort();
      } else if (status == 1) {
	break;
      }
    }


    switch (result) {
    case 'f':
      // FAIL
      // go around again
      //      printf("FAIL\n");
      break;

    case 'w':
      // WIN
      //      printf("WIN\n");
      action.sa_handler = SIG_DFL;
      sigaction(SIGCHLD, &action, NULL);
      sigaction(SIGUSR1, &action, NULL);

      close(pipefd[0]);
      close(pipefd[1]);
      return i;

    default:
      abort();
    }
  }

  abort();
}

static
int f_init_imagej_exec (int num_arg, const char * const *args, int bloblen,
                        const void *blob_data, const char *filter_name,
                        void **filter_args)
{
   g_assert(num_arg == 1);

   struct filter_instance *inst =
     (struct filter_instance *)malloc(sizeof(struct filter_instance));

   gchar *dirname = g_strdup_printf("%s/imagejfindXXXXXX", g_get_tmp_dir());

   if (mkdtemp(dirname) == NULL) {
     perror("Could not create temporary directory");
     exit(0);
   }

   if (chdir(dirname) < 0) {
     perror("Could not enter ImageJ directory");
     exit(0);
   }

   g_free(dirname);

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

   // start X server?!
   int display = start_x_server();
   char *display_str = g_strdup_printf("localhost:%d", display);
   setenv("DISPLAY", display_str, 1);
   printf("DISPLAY=%s\n", display_str);
   g_free(display_str);

   // go!
   char *ij_args[] = { "java", "-server",
		       "-cp", "ij.jar:ijloader.jar:.",
		       "ijloader.IJLoader", NULL };

   GError *err = NULL;
   int to_fd;
   int from_fd;
   g_spawn_async_with_pipes(NULL,
			    ij_args,
			    NULL, G_SPAWN_SEARCH_PATH,
			    NULL, NULL, NULL,
			    &to_fd,
			    &from_fd,
			    NULL,
			    &err);
   if (err != NULL) {
     fprintf (stderr, "Unable to spawn: %s\n", err->message);
     abort();
   }


   inst->ij_to_file = fdopen(to_fd, "w");
   inst->ij_from_file = fdopen(from_fd, "r");

   gsize len;
   char *tmp = (char *) g_base64_decode(args[0], &len);
   g_assert(tmp[len-1] == '\0');
   inst->macro_name = strdup(tmp);
   g_free(tmp);
   *filter_args = inst;

   return 0;
}

static int f_eval_imagej_exec (lf_obj_handle_t ohandle, void *filter_args)
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

int main(int argc, char **argv)
{
   if (argc == 2 && !strcmp(argv[1], "--version")) {
     printf("ImageJ " IMAGEJ_VERSION "\n");
     return 0;
   } else if (argc == 2 && !strcmp(argv[1], "--filter")) {
     lf_main(f_init_imagej_exec, f_eval_imagej_exec);
     return 0;
   } else {
     printf("Usage: %s {--filter|--version}\n", argv[0]);
     return 1;
   }
}

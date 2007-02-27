/*
 * MATLABFind: A Diamond application for interoperating with MATLAB
 *
 * Copyright (c) 2006-2007 Carnegie Mellon University. All rights reserved.
 * Additional copyrights may be listed below.
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution in the file named LICENSE.
 *
 * Technical and financial contributors are listed in the file named
 * CREDITS.
 */

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

#ifndef IMAGEJ_EXE_PATH
#error Must specify IMAGEJ_EXE_PATH as compilation flag
#endif

struct filter_instance {
   int ij_pid;
   int ij_to_fd;
   int ij_from_fd;
   FILE *ij_to_file;
   FILE *ij_from_file;
   int macro_len;
   char *macro;
};

static void transmit_image(int img_width, int img_height,
                           const unsigned char *diamond_buf, FILE *fp)
{
   int net_img_height = htonl(img_height);
   int net_img_width = htonl(img_width);

   printf("Sending %d x %d image...\n", img_height, img_width);
   
   fwrite(&net_img_width, sizeof(net_img_width), 1, fp);
   fwrite(&net_img_height, sizeof(net_img_height), 1, fp);
   
   int row, col;
   const unsigned char * diamond_ptr = diamond_buf;
   char zero = 0;
   
   for (row = 0; row < img_height; row++) {
      for (col = 0; col < img_width; col++) {
         fwrite(&zero, 1, 1, fp);
         fwrite(diamond_ptr, 3, 1, fp);
         diamond_ptr += 4;
      }
   }
}

static void transmit_macro(int macro_len, char *macro, FILE *fp)
{
   int net_macro_len = htonl(macro_len);

   printf("Sending macro...\n");
   fwrite(&net_macro_len, sizeof(net_macro_len), 1,  fp);
   fwrite(macro, macro_len, 1, fp);
}

static double get_result(FILE *fp, int fd)
{
   double result;
   fscanf(fp, "%lE", &result);
   return result;
}

int f_init_imagej_exec (int num_arg, char **args, int bloblen,
                        void *blob_data, const char *filter_name,
                        void **filter_args)
{
   int child_in_fd[2], child_out_fd[2];

   pipe(child_in_fd);
   pipe(child_out_fd);

   int child_pid = fork();

   if (child_pid == 0) {
      close(child_in_fd[1]);
      close(child_out_fd[0]);
      dup2(child_in_fd[0], STDIN_FILENO);
      dup2(child_out_fd[1], STDOUT_FILENO);
      
      if (chdir(IMAGEJ_EXE_PATH) < 0) {
         perror("Could not enter ImageJ directory");
         exit(0);
      }
      setenv("DISPLAY", "localhost:100", 1);

      execlp("java", "java", "-server", "-cp", "ij.jar:.", 
             "ijloader.IJLoader", NULL);
   } else {
      close(child_in_fd[0]);
      close(child_out_fd[1]);
 
      struct filter_instance *inst = 
         (struct filter_instance *)malloc(sizeof(struct filter_instance));
   
      inst->ij_pid = child_pid;
      inst->ij_to_fd = child_in_fd[1];
      inst->ij_from_fd = child_out_fd[0];
      inst->ij_to_file = fdopen(inst->ij_to_fd, "w");
      inst->ij_from_file = fdopen(inst->ij_from_fd, "r");
      inst->macro_len = bloblen;
      inst->macro = malloc(bloblen);
      if (bloblen > 0) {
         memcpy(inst->macro, blob_data, bloblen);
      }
   
      *filter_args = inst;

   }    
   
   return 0;
}

int f_eval_imagej_exec (lf_obj_handle_t ohandle, void *filter_args)
{
   struct filter_instance *inst = (struct filter_instance *)filter_args;

   size_t len;
   unsigned char *diamond_attr;

   int width, height;

   printf("Executing search...\n");
   
   lf_ref_attr(ohandle, "_rows.int", &len, &diamond_attr);
   height = *((int *) diamond_attr);

   lf_ref_attr(ohandle, "_cols.int", &len, &diamond_attr);
   width = *((int *) diamond_attr);

   lf_ref_attr(ohandle, "_rgb_image.rgbimage", &len, &diamond_attr);

   transmit_image(width, height, diamond_attr, inst->ij_to_file);
   transmit_macro(inst->macro_len, inst->macro, inst->ij_to_file);
   fflush(inst->ij_to_file);
   printf("New image + macro sent...\n");
   fflush(stdout);
   
   double result = get_result(inst->ij_from_file, inst->ij_from_fd);
   
   lf_write_attr(ohandle, "_matlab_ans.double", sizeof(double), (unsigned char *)&result);

   return (int)(result);
}

int f_fini_imagej_exec (void *filter_args)
{
   struct filter_instance *inst = (struct filter_instance *)filter_args;

   int status;
   kill(inst->ij_pid, SIGKILL);
   waitpid(inst->ij_pid, &status, 0);

   fclose(inst->ij_to_file);
   fclose(inst->ij_from_file);
   close(inst->ij_to_fd);
   close(inst->ij_from_fd);
   
   if (inst->macro) {
      free(inst->macro);
   }

   free(inst);

   return 0;
}

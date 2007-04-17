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
#include <sys/socket.h>
#include <sys/wait.h>
#include <signal.h>
#include <unistd.h>
#include <netdb.h>

#define PORT 6822

#ifndef IMAGEJ_EXE_PATH
#error Must specify IMAGEJ_EXE_PATH as compilation flag
#endif

struct filter_instance {
   int ij_fd;
   FILE *ij_file;
   int macro_len;
   char *macro;
};

static void transmit_image(int img_width, int img_height,
                           const unsigned char *diamond_buf, int fd)
{
   int net_img_height = htonl(img_height);
   int net_img_width = htonl(img_width);

   printf("Sending %d x %d image...\n", img_height, img_width);
   
   write(fd, &net_img_width, sizeof(net_img_width));
   write(fd, &net_img_height, sizeof(net_img_height));
   
   int row, col;
   const unsigned char * diamond_ptr = diamond_buf;
   char zero = 0;
   
   for (row = 0; row < img_height; row++) {
      for (col = 0; col < img_width; col++) {
         write(fd, &zero, 1);
         write(fd, diamond_ptr, 3);
         diamond_ptr += 4;
      }
   }
}

static void transmit_macro(int macro_len, char *macro, int fd)
{
   int net_macro_len = htonl(macro_len);

   printf("Sending macro...\n");
   write(fd, &net_macro_len, sizeof(net_macro_len));
   write(fd, macro, macro_len);
}

static double get_result(int fd)
{
   double result;
   char buf[512];
   read(fd, buf, 512);
   sscanf(buf, "%lE", &result);
   return result;
}

int f_init_imagej_exec (int num_arg, char **args, int bloblen,
                        void *blob_data, const char *filter_name,
                        void **filter_args)
{
   int ij_socket = socket(AF_INET, SOCK_STREAM, 0);
   
   struct hostent host, *result;
   char buf[1024];
   int h_errno;
   if (gethostbyname2_r("ijvm", AF_INET, &host, buf, 1024, &result, &h_errno) < 0) {
      *filter_args = NULL;
   } else {
       struct sockaddr_in *addr = (struct sockaddr_in *)(result->h_addr_list[0]);
       addr->sin_port = htons(PORT);
       if (connect(ij_socket, (struct sockaddr *)addr, result->h_length) < 0) {
         *filter_args = NULL;
       } else {
         struct filter_instance *inst = 
           (struct filter_instance *)malloc(sizeof(struct filter_instance));
   
         inst->ij_fd = ij_socket;
         inst->macro_len = bloblen;
         inst->macro = malloc(bloblen);
         if (bloblen > 0) {
            memcpy(inst->macro, blob_data, bloblen);
         }
   
        *filter_args = inst;
       } 
   }

   return 0;
}

int f_eval_imagej_exec (lf_obj_handle_t ohandle, void *filter_args)
{
   struct filter_instance *inst = (struct filter_instance *)filter_args;
   if (!inst)
     return 0;

   size_t len;
   unsigned char *diamond_attr;

   int width, height;

   printf("Executing search...\n");
   
   lf_ref_attr(ohandle, "_rows.int", &len, &diamond_attr);
   height = *((int *) diamond_attr);

   lf_ref_attr(ohandle, "_cols.int", &len, &diamond_attr);
   width = *((int *) diamond_attr);

   lf_ref_attr(ohandle, "_rgb_image.rgbimage", &len, &diamond_attr);

   transmit_image(width, height, diamond_attr, inst->ij_fd);
   transmit_macro(inst->macro_len, inst->macro, inst->ij_fd);
   printf("New image + macro sent...\n");
   fflush(stdout);
   
   double result = get_result(inst->ij_fd);
   
   lf_write_attr(ohandle, "_matlab_ans.double", sizeof(double), (unsigned char *)&result);

   return (int)(result);
}

int f_fini_imagej_exec (void *filter_args)
{
   struct filter_instance *inst = (struct filter_instance *)filter_args;
   if (!inst)
     return 0;

   close(inst->ij_fd);
   
   if (inst->macro) {
      free(inst->macro);
   }

   free(inst);

   return 0;
}

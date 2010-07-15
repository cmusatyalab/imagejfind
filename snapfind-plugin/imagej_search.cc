/*
 *  ImageJFind
 *  A Diamond application for interoperating with ImageJ
 *  Version 1
 *
 *  Copyright (c) 2002-2005 Intel Corporation
 *  Copyright (c) 2008 Carnegie Mellon University
 *  All Rights Reserved.
 *
 *  This software is distributed under the terms of the Eclipse Public
 *  License, Version 1.0 which can be found in the file named LICENSE.
 *  ANY USE, REPRODUCTION OR DISTRIBUTION OF THIS SOFTWARE CONSTITUTES
 *  RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT
 */

#include <pthread.h>
#include <stdlib.h>
#include <stdio.h>
#include <glib.h>
#include <gtk/gtk.h>
#include <assert.h>
#include <string.h>
#include <errno.h>
#include <sys/queue.h>
#include "lib_results.h"
#include "rgb.h"
#include "imagej_search.h"
#include "factory.h"
#include "quick_tar.h"

#define	MAX_DISPLAY_NAME	64

/* config file tokens that we write out */
#define SEARCH_NAME     "imagej_search"
#define EVAL_FUNCTION_ID    "EVAL_FUNCTION"
#define THRESHOLD_ID "THRESHOLD"
#define SOURCE_FOLDER_ID 	"SOURCE_FOLDER"



extern "C" {
void search_init();
}

/*
 * Initialization function that creates the factory and registers
 * it with the rest of the UI.
 */
void
search_init()
{
	imagej_factory *fac;
	fac = new imagej_factory;
	imagej_codec_factory *fac2;
	fac2 = new imagej_codec_factory;
	factory_register(fac);
	//	factory_register_codec(fac2);  // also does codec
}



imagej_search::imagej_search(const char *name, char *descr)
		: img_search(name, descr)
{
	eval_function = strdup("eval");
	threshold = strdup("0");
	source_folder = NULL;

	edit_window = NULL;

	return;
}

imagej_search::~imagej_search()
{
	if (eval_function) {
		free(eval_function);
	}
	if (threshold) {
		free(threshold);
	}
	if (source_folder) {
		free(source_folder);
	}

	free(get_auxiliary_data());
	return;
}


int
imagej_search::handle_config(int nconf, char **data)
{
	int	err;

	if (strcmp(EVAL_FUNCTION_ID, data[0]) == 0) {
		gsize	len;
		char	*tmp;

		assert(nconf > 1);
		tmp = (char *) g_base64_decode(data[1], &len);
		assert(tmp[len-1] == '\0');
		eval_function = strdup(tmp);
		g_free(tmp);
		err = 0;
	} else if (strcmp(THRESHOLD_ID, data[0]) == 0) {
		assert(nconf > 1);
		threshold = strdup(data[1]);
		assert(threshold != NULL);
		err = 0;
	} else if (strcmp(SOURCE_FOLDER_ID, data[0]) == 0) {
		assert(nconf > 1);
		source_folder = strdup(data[1]);
		assert(source_folder != NULL);
		err = 0;
	} else {
		err = img_search::handle_config(nconf, data);
	}

	return(err);
}


static void
cb_edit_done(GtkButton *item, gpointer data)
{
	GtkWidget * widget = (GtkWidget *)data;
	gtk_widget_destroy(widget);
}

static void
cb_close_edit_window(GtkWidget* item, gpointer data)
{
	imagej_search *    search;
	search = (imagej_search *)data;
	search->close_edit_win();
}


void
imagej_search::edit_search()
{
	GtkWidget *     widget;
	GtkWidget *     box;
	GtkWidget *     hbox;
	GtkWidget *     table;
	char        name[MAX_DISPLAY_NAME];

	/* see if it already exists */
	if (edit_window != NULL) {
		/* raise to top ??? */
		gdk_window_raise(GTK_WIDGET(edit_window)->window);
		return;
	}

	edit_window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
	snprintf(name, MAX_DISPLAY_NAME - 1, "Edit %s", get_name());
	name[MAX_DISPLAY_NAME -1] = '\0';
	gtk_window_set_title(GTK_WINDOW(edit_window), name);
	g_signal_connect(G_OBJECT(edit_window), "destroy",
	                 G_CALLBACK(cb_close_edit_window), this);

	box = gtk_vbox_new(FALSE, 10);
	gtk_container_add(GTK_CONTAINER(edit_window), box);

	hbox = gtk_hbox_new(FALSE, 10);
	gtk_box_pack_start(GTK_BOX(box), hbox, FALSE, TRUE, 0);

	widget = gtk_button_new_with_label("Close");
	g_signal_connect(G_OBJECT(widget), "clicked",
	                 G_CALLBACK(cb_edit_done), edit_window);
	GTK_WIDGET_SET_FLAGS(widget, GTK_CAN_DEFAULT);
	gtk_box_pack_start(GTK_BOX(hbox), widget, FALSE, TRUE, 0);

	/*
	 * Get the controls from the img_search.
	 */
	widget = img_search_display();
	gtk_box_pack_start(GTK_BOX(box), widget, FALSE, TRUE, 0);

	/*
 	 * To make the layout look a little cleaner we use a table
	 * to place all the fields.  This will make them be nicely
	 * aligned.
	 */
	table = gtk_table_new(4, 2, FALSE);
        gtk_table_set_row_spacings(GTK_TABLE(table), 2);
        gtk_table_set_col_spacings(GTK_TABLE(table), 4);
        gtk_container_set_border_width(GTK_CONTAINER(table), 10);
	gtk_box_pack_start(GTK_BOX(box), table, FALSE, TRUE, 0);

	/* set the first row label and text entry for the eval function */
	widget = gtk_label_new("Macro name");
	gtk_label_set_justify(GTK_LABEL(widget), GTK_JUSTIFY_LEFT);
	gtk_table_attach_defaults(GTK_TABLE(table), widget, 0, 1, 0, 1);
	eval_function_entry = gtk_entry_new();
	gtk_table_attach_defaults(GTK_TABLE(table), eval_function_entry, 1, 2, 0, 1);
	if (eval_function != NULL) {
		gtk_entry_set_text(GTK_ENTRY(eval_function_entry), eval_function);
	}

	/* set the third row label and text entry for the threshold */
	widget = gtk_label_new("Threshold");
	gtk_label_set_justify(GTK_LABEL(widget), GTK_JUSTIFY_LEFT);
	gtk_table_attach_defaults(GTK_TABLE(table), widget, 0, 1, 2, 3);
	threshold_entry = gtk_entry_new();
	gtk_table_attach_defaults(GTK_TABLE(table), threshold_entry, 1, 2, 2, 3);
	if (threshold != NULL) {
		gtk_entry_set_text(GTK_ENTRY(threshold_entry), threshold);
	}

	/* set the fourth row label and file chooser button for the source directory */
        widget = gtk_label_new("Source folder");
	gtk_label_set_justify(GTK_LABEL(widget), GTK_JUSTIFY_LEFT);
	gtk_table_attach_defaults(GTK_TABLE(table), widget, 0, 1, 3, 4);
	source_folder_button = gtk_file_chooser_button_new("Select a Folder",
							   GTK_FILE_CHOOSER_ACTION_SELECT_FOLDER);
	gtk_table_attach_defaults(GTK_TABLE(table), source_folder_button, 1, 2, 3, 4);
	if (source_folder != NULL) {
		gtk_file_chooser_set_uri(GTK_FILE_CHOOSER(source_folder_button), source_folder);
	}

	/* make everything visible */
	gtk_widget_show_all(edit_window);

	return;
}



/*
 * This method reads the values from the current edit
 * window if there is an active one.
 */

void
imagej_search::save_edits()
{
	int fd;
	int blob_len;
	gchar *name_used;
	gboolean success;
	gchar *blob_data;
	gchar *tmp;

	if (edit_window == NULL) {
		return;
	}

	if (eval_function != NULL) {
		free(eval_function);
	}
	if (threshold != NULL) {
		free(threshold);
	}
	if (source_folder != NULL) {
		free(source_folder);
	}

	eval_function = strdup(gtk_entry_get_text(GTK_ENTRY(eval_function_entry)));
	assert(eval_function != NULL);

	threshold = strdup(gtk_entry_get_text(GTK_ENTRY(threshold_entry)));
	assert(threshold != NULL);
	source_folder = strdup(gtk_file_chooser_get_uri(GTK_FILE_CHOOSER(source_folder_button)));
	assert(source_folder != NULL);

	/* blob */
	free(get_auxiliary_data());

	fd = g_file_open_tmp(NULL, &name_used, NULL);
	g_assert(fd >= 0);

	//	printf("quick tar: %s\n", source_folder);
	tmp = g_filename_from_uri(source_folder, NULL, NULL);
	blob_len = tar_blob(tmp, fd);
	g_free(tmp);
	g_assert(blob_len >= 0);

	success = g_file_get_contents(name_used, &blob_data, NULL, NULL);
	g_assert(success);

	set_auxiliary_data(blob_data);
	set_auxiliary_data_length(blob_len);
	//	printf(" blob length: %d\n", blob_len);

	// save name
	img_search::save_edits();
}


void
imagej_search::close_edit_win()
{
	save_edits();

	/* call parent to give them a chance to cleanup */
	img_search::close_edit_win();

	edit_window = NULL;
}

/*
 * This write the relevant section of the filter specification file
 * for this search.
 */

void
imagej_search::write_fspec(FILE *ostream)
{
	gchar *b64_tmp;

	if (strcmp("RGB", get_name()) == 0) {
		fprintf(ostream, "FILTER  RGB\n");
	} else {
		fprintf(ostream, "FILTER  %s  # dependencies \n", get_name());
		fprintf(ostream, "REQUIRES RGB\n");
	}

	fprintf(ostream, "\n");
	fprintf(ostream, "THRESHOLD  %s\n", threshold);
	fprintf(ostream, "MERIT  10000\n");
	fprintf(ostream, "EVAL_FUNCTION  f_eval_imagej_exec  # eval function \n");
	fprintf(ostream, "INIT_FUNCTION  f_init_imagej_exec  # init function \n");
	fprintf(ostream, "FINI_FUNCTION  f_fini_imagej_exec  # fini function \n");

	b64_tmp = g_base64_encode((const guchar *) eval_function, strlen(eval_function) + 1);
	fprintf(ostream, "ARG  %s\n", b64_tmp);
	g_free(b64_tmp);

	fprintf(ostream, "\n");
	fprintf(ostream, "\n");
}

void
imagej_search::write_config(FILE *ostream, const char *dirname)
{
	gchar *b64_tmp;

 	fprintf(ostream, "SEARCH %s %s\n", SEARCH_NAME, get_name());

	b64_tmp = g_base64_encode((const guchar *) eval_function, strlen(eval_function) + 1);
 	fprintf(ostream, "%s %s\n", EVAL_FUNCTION_ID, b64_tmp);
	g_free(b64_tmp);

 	fprintf(ostream, "%s %s \n", THRESHOLD_ID, threshold);

	if (source_folder != NULL) {
	 	fprintf(ostream, "%s %s \n", SOURCE_FOLDER_ID, source_folder);
	}
}

/* Region match isn't meaningful for this search */
void
imagej_search::region_match(RGBImage *img, bbox_list_t *blist)
{
	return;
}

bool
imagej_search::is_editable(void)
{
	return true;
}

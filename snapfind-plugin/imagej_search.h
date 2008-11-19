/*
 *  ImageJFind
 *  A Diamond application for interoperating with ImageJ
 *  Version 1
 *
 *  Copyright (c) 2008 Carnegie Mellon University
 *  All Rights Reserved.
 *
 *  This software is distributed under the terms of the Eclipse Public
 *  License, Version 1.0 which can be found in the file named LICENSE.
 *  ANY USE, REPRODUCTION OR DISTRIBUTION OF THIS SOFTWARE CONSTITUTES
 *  RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT
 */

#ifndef	_IMAGEJ_SEARCH_H_
#define	_IMAGEJ_SEARCH_H_	1

#include <gtk/gtk.h>
#include "img_search.h"

class imagej_search: public img_search {
public:
	imagej_search(const char *name, char *descr);
	~imagej_search(void);

	void	save_edits();
	void	edit_search();
	void 	write_fspec(FILE* stream);
	void	write_config(FILE* stream, const char *data_dir);
	virtual	int	handle_config(int num_conf, char **datav);
	void	close_edit_win();
	bool	is_editable();
	virtual void 	region_match(RGBImage *img, bbox_list_t *blist);

private:
	char *		eval_function;
	char *		init_function;
	char *          threshold;
	char *          source_folder;
	GtkWidget       *eval_function_entry;
	GtkWidget       *init_function_entry;
	GtkWidget       *threshold_entry;
	GtkWidget	*source_folder_button;

	GtkWidget       *edit_window;
};


class imagej_factory: public img_factory {
public:
	imagej_factory() {
		set_name("ImageJ");
		set_description("imagej_search");
	}
	img_search *create(const char *name) {
		return new imagej_search(name, "ImageJ");
	}
	int is_example() {
		return(0);
	}
};

class imagej_codec_factory: public img_factory {
public:
	imagej_codec_factory() {
		set_name("ImageJ");
		set_description("RGB");
	}
	img_search *create(const char *name) {
		return new imagej_search(name, "RGB");
	}
	int is_example() {
		return(0);
	}
};


#ifdef __cplusplus
extern "C"
{
#endif


#ifdef __cplusplus
}
#endif

#endif	/* !_IMAGEJ_SEARCH_H_ */

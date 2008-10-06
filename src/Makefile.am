## Process this file with automake to produce Makefile.in

gladedir = $(datadir)/imagejfind/glade
filterdir = $(datadir)/imagejfind/filter

EXTRA_DIST = fil_imagej_exec.c

INCLUDES = \
	-DPACKAGE_DATA_DIR=\""$(datadir)"\" \
	-DPACKAGE_LOCALE_DIR=\""$(prefix)/$(DATADIRNAME)/locale"\" \
	-DIMAGEJFIND_GLADEDIR=\""$(gladedir)"\" \
	-DIMAGEJFIND_FILTERDIR=\""$(filterdir)"\" \
	@OPENDIAMOND_CFLAGS@ @GLADE_CFLAGS@

bin_PROGRAMS = imagejfind

imagejfind_SOURCES = \
	main.c imagejfind.h \
	define.c define.h \
	investigate.cpp investigate.h \
	util.c util.h \
	quick_tar.c quick_tar.h \
	diamond_interface.c diamond_interface.h

imagejfind_LDADD = @OPENDIAMOND_LIBS@ @GLADE_LIBS@

imagejfind_LDFLAGS = -export-dynamic

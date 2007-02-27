## Process this file with automake to produce Makefile.in

gladedir = $(datadir)/imagejfind/glade
filterdir = $(datadir)/imagejfind/filter

INCLUDES = \
	-DPACKAGE_DATA_DIR=\""$(datadir)"\" \
	-DPACKAGE_LOCALE_DIR=\""$(prefix)/$(DATADIRNAME)/locale"\" \
	-DIMAGEJFIND_GLADEDIR=\""$(gladedir)"\" \
	-DIMAGEJFIND_FILTERDIR=\""$(filterdir)"\" \
	@glade_CFLAGS@

bin_PROGRAMS = imagejfind

imagejfind_SOURCES = \
	main.c imagejfind.h \
	define.c define.h \
	investigate.cpp investigate.h \
	util.c util.h \
	diamond_interface.c diamond_interface.h

imagejfind_LDADD = @glade_LIBS@

imagejfind_LDFLAGS = -export-dynamic

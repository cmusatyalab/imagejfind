Build Requirements
------------------

- JDK
- glib2
- OpenDiamond
- libarchive
- zip/unzip


Building
--------

1. `python get-latest-imagej.py`
2. `make`
3. `make install`.  To install into different directories, pass `BINDIR`
and/or `FILTER_DIR`.


Running
-------

Xvfb and a JRE must be installed on the Diamond servers.  ImageJ itself
need not be preinstalled, since `fil_imagej_exec` contains an embedded copy.


Writing Macros
--------------

See https://github.com/cmusatyalab/opendiamond/wiki/ImageJMacros.

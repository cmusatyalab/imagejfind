INSTALL := install
CFLAGS := -fPIC -O2 -g -m32 -Wall -Wextra -Iquick-tar
SNAPFIND_LIBDIR=/opt/snapfind/lib

IJZIP := ij-latest.zip

all: filter-code/fil_imagej_exec snapfind-plugin/imagej_search.so

# quick tar
quick-tar/quick_tar.o: quick-tar/quick_tar.c quick-tar/quick_tar.h
	gcc $(CFLAGS) -o $@ -c quick-tar/quick_tar.c

# filter code
filter-code/fil_imagej_exec: filter-code/fil_imagej_exec.c filter-code/imagej-bin.h filter-code/ijloader-bin.h filter-code/diamond_filter-bin.h quick-tar/quick_tar.o PrintImageJVersion.class
	export PKG_CONFIG_PATH=/opt/diamond-filter-kit/lib/pkgconfig:$$PKG_CONFIG_PATH; gcc $(CFLAGS) -o $@ filter-code/fil_imagej_exec.c quick-tar/quick_tar.o $$(pkg-config opendiamond glib-2.0 --cflags --libs) -I/opt/diamond-filter-kit/include -L/opt/diamond-filter-kit/lib $$(pkg-config libarchive --cflags --libs --static) -DIMAGEJ_VERSION=\"$(shell java PrintImageJVersion)\"

# don't remove ij.jar dependency, the version string is inlined at compile time
PrintImageJVersion.class: ij.jar
	javac -classpath ij.jar PrintImageJVersion.java

$(IJZIP):
	$(error Run "python get-latest-imagej.py" to get $(IJZIP))

filter-code/imagej-bin.h: $(IJZIP) filter-code/encapsulate
	./filter-code/encapsulate imagej_bin < $< > $@

filter-code/ijloader-bin.h: ijloader.jar filter-code/encapsulate
	./filter-code/encapsulate ijloader_bin < $< > $@

filter-code/diamond_filter-bin.h: diamond_filter.jar filter-code/encapsulate
	./filter-code/encapsulate diamond_filter_bin < $< > $@

filter-code/encapsulate: filter-code/encapsulate.c
	gcc -O2 -Wall -g $< -o $@


ij.jar: $(IJZIP)
	unzip -j -o $(IJZIP) ImageJ/$@
	touch ij.jar

ijloader.jar: ijloader/src/ijloader/IJLoader.java ij.jar
	mkdir -p ijloader/bin
	javac -source 1.5 -target 1.5 -cp ij.jar -d ijloader/bin $<
	jar cf $@ -C ijloader/bin/ .

diamond_filter.jar: diamond_filter/src/Diamond_Filter.java ijloader.jar
	mkdir -p diamond_filter/bin
	javac -source 1.5 -target 1.5 -cp ij.jar:ijloader.jar -d diamond_filter/bin $<
	jar cf $@ -C diamond_filter/bin/ .


# snapfind plugin
snapfind-plugin/imagej_search.so: snapfind-plugin/imagej_search.h snapfind-plugin/imagej_search.cc quick-tar/quick_tar.o PrintImageJVersion.class
	g++ $(CFLAGS) -I/opt/snapfind/include -shared -o $@ snapfind-plugin/imagej_search.cc $$(pkg-config --cflags opendiamond) $$(pkg-config --cflags --libs gtk+-2.0) quick-tar/quick_tar.o -DIMAGEJ_VERSION=\"$(shell java PrintImageJVersion)\"


# clean
clean:
	$(RM) -r filter-code/fil_imagej_exec filter-code/*-bin.h \
		filter-code/encapsulate *.jar \
		diamond_filter/bin ijloader/bin \
		quick-tar/*.o snapfind-plugin/*.so *.class

# install
install: all snapfind-plugin/imagej.sf_conf
	$(INSTALL) filter-code/fil_imagej_exec snapfind-plugin/*.so $(SNAPFIND_LIBDIR)
	$(INSTALL) -m 644 snapfind-plugin/*.sf_conf $(SNAPFIND_LIBDIR)


.DUMMY: all clean install

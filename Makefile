CFLAGS := -fPIC -O2 -g -m32 -Wall -Wextra -Iquick-tar

IJZIP := ij141.zip

all: filter-code/fil_imagej_exec.so

# quick tar
quick-tar/quick_tar.o: quick-tar/quick_tar.c quick-tar/quick_tar.h
	gcc $(CFLAGS) -o $@ -c quick-tar/quick_tar.c

# filter code
filter-code/fil_imagej_exec.so: filter-code/fil_imagej_exec.c filter-code/imagej-bin.h filter-code/ijloader-bin.h filter-code/diamond_filter-bin.h quick-tar/quick_tar.o
	export PKG_CONFIG_PATH=/opt/diamond-filter-kit/lib/pkgconfig:$$PKG_CONFIG_PATH; gcc $(CFLAGS) -shared -o $@ filter-code/fil_imagej_exec.c quick-tar/quick_tar.o $$(pkg-config opendiamond --cflags) $$(pkg-config glib-2.0 --cflags --libs --static)


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

ijloader.jar: ijloader/src/ijloader/IJLoader.java ij.jar
	mkdir -p ijloader/bin
	javac -cp ij.jar -d ijloader/bin $<
	jar cf $@ -C ijloader/bin/ .

diamond_filter.jar: diamond_filter/src/Diamond_Filter.java ijloader.jar
	mkdir -p diamond_filter/bin
	javac -cp ij.jar:ijloader.jar -d diamond_filter/bin $<
	jar cf $@ -C diamond_filter/bin/ .


# snapfind plugin


clean:
	$(RM) -r filter-code/fil_imagej_exec.so filter-code/*-bin.h \
		filter-code/encapsulate *.jar \
		diamond_filter/bin ijloader/bin \
		quick-tar/*.o

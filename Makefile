IJZIP := ij141.zip

all: fil_imagej_exec.so

fil_imagej_exec.so: src/fil_imagej_exec.c src/imagej-bin.h src/ijloader-bin.h src/diamond_filter-bin.h src/quick_tar.c src/quick_tar.h
	export PKG_CONFIG_PATH=/opt/diamond-filter-kit/lib/pkgconfig:$$PKG_CONFIG_PATH; gcc -fPIC -O2 -g -m32 -Wall -Wextra -shared -o $@ src/fil_imagej_exec.c src/quick_tar.c $$(pkg-config opendiamond --cflags) $$(pkg-config glib-2.0 --cflags --libs --static)


src/imagej-bin.h: $(IJZIP) src/encapsulate
	./src/encapsulate imagej_bin < $< > $@

src/ijloader-bin.h: ijloader.jar src/encapsulate
	./src/encapsulate ijloader_bin < $< > $@

src/diamond_filter-bin.h: diamond_filter.jar src/encapsulate
	./src/encapsulate diamond_filter_bin < $< > $@

src/encapsulate: src/encapsulate.c
	gcc -O2 -Wall -g $< -o $@


clean:
	$(RM) -r fil_imagej_exec.so src/*-bin.h src/encapsulate *.jar \
		diamond_filter/bin ijloader/bin

ij.jar: $(IJZIP)
	unzip -j -o $(IJZIP) ImageJ/$@

ijloader.jar: ijloader/src/ijloader/IJLoader.java ij.jar
	mkdir -p ijloader/bin
	javac -cp ij.jar -d ijloader/bin $<
	jar cvf $@ -C ijloader/bin/ .

diamond_filter.jar: diamond_filter/src/Diamond_Filter.java ijloader.jar
	mkdir -p diamond_filter/bin
	javac -cp ij.jar:ijloader.jar -d diamond_filter/bin $<
	jar cvf $@ -C diamond_filter/bin/ .

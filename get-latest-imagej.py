#!/usr/bin/python

import os
import shutil
import subprocess
import urllib2

OUTPUT = 'ij-latest.zip'
OUTPUT_BASEDIR = 'ImageJ'
OUTPUT_JAR = os.path.join(OUTPUT_BASEDIR, 'ij.jar')

URL = 'http://rsb.info.nih.gov/ij/'
ZIPS = URL + 'download/zips/'
JARS = URL + 'download/jars/'
VERSION_LIST = JARS + 'list.txt'
UPGRADE_JAR = URL + 'upgrade/ij.jar'
CHARS = ''.join(map(chr, range(ord('a'),ord('z')+1)))


# remove any previous unpacked dir
if os.path.exists(OUTPUT_BASEDIR):
    shutil.rmtree(OUTPUT_BASEDIR)

# get path to latest zip
vl = urllib2.urlopen(VERSION_LIST)
url = ZIPS + vl.readline().strip().rstrip(CHARS).replace('.', '').replace('v', 'ij') + '.zip'
print "latest zip: " + url
latest = urllib2.urlopen(url)
vl.close()

# get it
out = open(OUTPUT, "w")
shutil.copyfileobj(latest, out)
out.close()
latest.close()

# unpack it
subprocess.call(['unzip', '-q', OUTPUT])

# get latest jar, add to directory
print "latest jar: " + UPGRADE_JAR
uj = urllib2.urlopen(UPGRADE_JAR)
out = open(OUTPUT_JAR, 'w')
shutil.copyfileobj(uj, out)
out.close()
uj.close()

# repack zip
os.unlink(OUTPUT)
subprocess.call(['zip', '-qr', OUTPUT, OUTPUT_BASEDIR])

# remove unpacked dir
shutil.rmtree(OUTPUT_BASEDIR)

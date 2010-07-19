#!/usr/bin/python

import os
import subprocess
import urllib2
import zipfile

OUTPUT = 'ij-latest.zip'
OUTPUT_JAR = 'ImageJ/ij.jar'

URL = 'http://rsb.info.nih.gov/ij/'
ZIPS = URL + 'download/zips/'
JARS = URL + 'download/jars/'
VERSION_LIST = JARS + 'list.txt'
UPGRADE_JAR = URL + 'upgrade/ij.jar'
CHARS = ''.join(map(chr, range(ord('a'),ord('z')+1)))


# get path to latest zip
vl = urllib2.urlopen(VERSION_LIST)
url = ZIPS + vl.readline().strip().rstrip(CHARS).replace('.', '').replace('v', 'ij') + '.zip'
print "latest zip: " + url
latest = urllib2.urlopen(url)
vl.close()

# get it
out = open(OUTPUT, "w")

out.write(latest.read())
out.close()
latest.close()


# delete the old jar in the zip
subprocess.call(['zip', OUTPUT, '-d', OUTPUT_JAR])

# get latest jar, add to zip
print "latest jar: " + UPGRADE_JAR
uj = urllib2.urlopen(UPGRADE_JAR)
zip = zipfile.ZipFile(OUTPUT, "a")
zip.writestr(OUTPUT_JAR, uj.read())
zip.close()
uj.close()



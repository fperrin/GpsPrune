#!/usr/bin/env python
# -*- coding: utf-8 -*-

# © 2010, David Paleino <dapal@debian.org>
#
# This script is released under the GNU General Public License, version 2.

from urllib2 import urlopen
import re

base = "https://dds.cr.usgs.gov/srtm/version2_1/SRTM3/%s/"
regions = ["Eurasia", "North_America", "Australia", "Islands", "South_America", "Africa"]

for reg in regions:
    url = base % reg
    tiles = []
    for line in urlopen(url).readlines():
        if line.startswith("<li>"):
            match = re.match("^<li><.*> ([^>]*)<.*>", line)
            if match:
                tiles.append(match.group(1).replace(".hgt.zip", ""))
    f = open("tiles%s.txt" % (regions.index(reg)+1), "w")
    f.write('\n'.join([reg] + tiles))
    f.close()

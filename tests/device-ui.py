#!/usr/bin/env python3
import os
import subprocess,sys,xml.etree.ElementTree as ET,os
adb=os.environ.get('ADB','adb')
if len(sys.argv)>1: subprocess.run([adb,'shell','input',*sys.argv[1:]],check=True)
subprocess.run([adb,'shell','uiautomator','dump','/sdcard/neo-studio-window.xml'],stdout=subprocess.DEVNULL,check=True)
root=ET.fromstring(subprocess.check_output([adb,'shell','cat','/sdcard/neo-studio-window.xml']))
for n in root.iter('node'):
 text=n.get('text') or n.get('content-desc')
 if text: print(text,n.get('bounds',''))

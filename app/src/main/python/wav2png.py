#!/usr/bin/env python 
import numpy as np
# https://github.com/librosa/librosa
import librosa
import librosa.display
import argparse
import os
from PIL import Image
from PIL import PngImagePlugin
import json
import math


FLAGS = None

def main(path):
    print(path)
    result = wav2Spect_single(path,None,1024,256,None)
    return result


def get_subdirs(a_dir):
    """ Returns a list of sub directory names in a_dir """ 
    return [name for name in os.listdir(a_dir)
            if (os.path.isdir(os.path.join(a_dir, name)) and not (name.startswith('.')))]


def listDirectory(directory, fileExtList):                                        
    """Returns a list of file info objects in directory that extension in the list fileExtList - include the . in your extension string"""
    fnameList = [os.path.normcase(f)
                for f in os.listdir(directory)
                    if (not(f.startswith('.')))]            
    fileList = [os.path.join(directory, f) 
               for f in fnameList
                if os.path.splitext(f)[1] in fileExtList]  
    return fileList , fnameList


def wav2stft(fname, srate, fftSize, fftHop, dur) :
    try:
        audiodata, samplerate = librosa.load(fname, sr=srate, mono=True, duration=dur)
        #print(np.amax(np.abs(audiodata)))
        #print(np.amin(np.abs(audiodata)))
        #print(audiodata[50:70])
    except:
        print('can not read ' + fname)
        return
    
    if srate == None:
        print('Using native samplerate of ' + str(samplerate))
    S = np.abs(librosa.stft(audiodata, n_fft=fftSize, hop_length=fftHop, win_length=fftSize,  center=True))
    return S


def log_scale(x):
    output = np.log1p(x)
    return output


def findMinMax(img):
    return np.amin(img),np.amax(img)


def logSpect2PNG(outimg, fname, lwinfo=None) :
    
    info = PngImagePlugin.PngInfo()
    lwinfo = lwinfo or {}
    lwinfo['fileMin'] = str(np.amin(outimg))
    lwinfo['fileMax'] = str(np.amax(outimg))
    info.add_text('meta',json.dumps(lwinfo)) #info required to reverse scaling
    
    shift = int(lwinfo['scaleMax']) - int(lwinfo['scaleMin'])
    SC2 = 255*(outimg-int(lwinfo['scaleMin']))/shift
    savimg = Image.fromarray(np.flipud(SC2))

    pngimg = savimg.convert('L')  
    pngimg.save(fname,pnginfo=info)
 
 
def checkScaling(topdir, outdir, srate, fftSize, fftHop, dur):
    print("Now determining maximum log magnitude value in dataset for scaling to png...") 
    subdirs = get_subdirs(topdir)
    max_mag = 0
    min_mag = 0
    for subdir in subdirs:
        
        fullpaths, _ = listDirectory(topdir + '/' + subdir, '.wav') 
        
        for idx in range(len(fullpaths)) : 
            fname = os.path.basename(fullpaths[idx])
            
            D = wav2stft(fullpaths[idx], srate, fftSize, fftHop, dur)
            D = log_scale(D)
            minM,maxM = findMinMax(D)
            if minM > min_mag:
                min_mag = minM
            if maxM > max_mag:
                max_mag = maxM
    
    print("Dataset: min magnitude=",min_mag,"max magnitude=",max_mag)
    minScale = int(math.floor(min_mag))
    maxScale = int(math.ceil(max_mag)) 
    #print("New scale: min magnitude=",minScale,"max magnitude=",maxScale)
    print("Using [{0},{1}] -> [0,255] for png conversion".format(minScale,maxScale))
    pnginfo = {}
    pnginfo['datasetMin'] = str(min_mag)
    pnginfo['datasetMax'] = str(max_mag)
    pnginfo['scaleMin'] = str(minScale)
    pnginfo['scaleMax'] = str(maxScale)
    return pnginfo

    
def wav2Spect(topdir, outdir, srate, fftSize, fftHop, dur, pnginfo) :
    
    subdirs = get_subdirs(topdir)
    count = 0
    for subdir in subdirs:
        
        fullpaths, _ = listDirectory(topdir + '/' + subdir, '.wav') 
        
        for idx in range(len(fullpaths)) : 
            fname = os.path.basename(fullpaths[idx])
            D = wav2stft(fullpaths[idx], srate, fftSize, fftHop, dur)
            D = log_scale(D)
            
            try:
                os.stat(outdir + '/' + subdir) # test for existence
            except:
                os.makedirs(outdir + '/' + subdir) # create if necessary
            
            print(str(count) + ': ' + subdir + '/' + os.path.splitext(fname)[0])

            logSpect2PNG(D, outdir+'/'+subdir+'/'+os.path.splitext(fname)[0]+'.png', lwinfo=pnginfo)
            
            count +=1
    print("COMPLETE") 
    
    
def wav2Spect_single(filename, srate, fftSize, fftHop, dur) :
    D = wav2stft(filename, srate, fftSize, fftHop, dur)
    D = log_scale(D)
    minM,maxM = findMinMax(D)
    minScale = int(math.floor(minM))
    maxScale = int(math.ceil(maxM)) 
    pnginfo = {}
    pnginfo['datasetMin'] = str(minM)
    pnginfo['datasetMax'] = str(maxM)
    pnginfo['scaleMin'] = str(minScale)
    pnginfo['scaleMax'] = str(maxScale)
        
    print(str(0) + ': ' + os.path.splitext(filename)[0])
    logSpect2PNG(D, os.path.splitext(filename)[0] +'.png',pnginfo)
    result = os.path.splitext(filename)[0] +'.png'
    print("COMPLETE") 
    return result
    
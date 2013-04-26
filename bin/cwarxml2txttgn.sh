#!/bin/bash

indir=${1%/}
outdir=${2%/}

if [ ! -e $outdir ]; then
    mkdir $outdir
fi

for f in $indir/*.xml
do
  filename=$(basename $f)
  filename=${filename%.*}
  grep '<milestone unit="sentence"' $f | sed -re 's/tgn,([^"]+)">([^<]+)/>tgn,\1-\2-]]/' | sed -re 's/tgn,([^"]+)-(\w+) (\w+)-]]/tgn,\1-\2-\3-]]/' | sed 's/<[^<>]*>//g' > $outdir/$filename.txt
done

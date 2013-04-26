#!/bin/bash

corpusname=$1; # tr or cwar
split=$2; # dev or test
topidmethod=$3; # gt or ner
modelsdir=wistr-models-$corpusname$split/;
if [ corpusname == "cwar" ]; then
    sercorpusprefix=cwar
else
    sercorpusprefix=trf
fi
if [ corpusname == "cwar" ]; then
    sercorpussuffix="-20spd"
else
    sercorpussuffix=""
fi
sercorpusfile=$sercorpusprefix$split-$topidmethod-g1dpc$sercorpussuffix.ser.gz;
corpusdir=${4%/}/$split/; # fourth argument is path to corpus in XML format
if [ corpusname == "cwar" ]; then
    logfileprefix=cwar
else
    logfileprefix=trconll
fi
logfile=enwiki-$logfileprefix$split-100.log;

mem=8g;

function printres {

    if [ $topidmethod == "ner" ]; then

        precision=`grep -A35 "$1" temp-results.txt | grep "P: " | sed -e 's/^.*: //'`
        recall=`grep -A35 "$1" temp-results.txt | grep "R: " | sed -e 's/^.*: //'`
        fscore=`grep -A35 "$1" temp-results.txt | grep "F: " | sed -e 's/^.*: //'`

        echo $1 "&" $precision "&" $recall "&" $fscore

    else

        mean=`grep -A35 "$1" temp-results.txt | grep "Mean error distance (km): " | sed -e 's/^.*: //'`
        median=`grep -A35 "$1" temp-results.txt | grep "Median error distance (km): " | sed -e 's/^.*: //'`
        accuracy=`grep -A35 "$1" temp-results.txt | grep "F: " | sed -e 's/^.*: //'`
        
        echo $1 "&" $mean "&" $median "&" $accuracy

    fi
}

#function getmean {
#    echo `grep -A25 "$1" temp-results.txt | grep "Mean error distance (km): " | sed -e 's/^.*: //'`
#}

if [ -e temp-results.txt ]; then
    rm temp-results.txt
fi

# Good to go
echo "\oracle" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -r random -oracle >> temp-results.txt
printres "\oracle"

# Good to go
for i in 1 2 3
do
  echo "\rand"$i >> temp-results.txt
  fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -r random >> temp-results.txt
  printres "\rand"$i
done

# Good to go
echo "\population" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -r pop >> temp-results.txt
printres "\population"

# Good to go
for i in 1 2 3
do
  echo "\spider"$i >> temp-results.txt
  fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -r wmd -it 10 >> temp-results.txt
  printres "\spider"$i
done

#echo "\tripdl" >> temp-results.txt
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r prob -pdg
#printres "\tripdl"

#echo "\wistr" >> temp-results.txt
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r maxent
#printres "\wistr"

#echo '--- (Necessary for next step) ---';
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r prob -pme
#echo '---';

#echo "\wistr+\spider" >> temp-results.txt
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r wmd -it 10 -rwf
#printres "\wistr+\spider"

#echo "\trawl" >> temp-results.txt
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r prob
#printres "\trawl"

#echo "\trawl+\spider" >> temp-results.txt
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r wmd -it 10 -rwf
#printres "\trawl+\spider"

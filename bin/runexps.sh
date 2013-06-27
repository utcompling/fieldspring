#!/bin/bash

corpusname=$1; # tr or cwar
split=$2; # dev or test
topidmethod=$3; # gt or ner
modelsdir=wistr-models-$corpusname$split-gt/;
listrmodelsdir=listr-models-$corpusname$split-gt/;
wistrlistrmodelsdir=wistrlistr-models-$corpusname$split-gt/;
if [ $corpusname == "cwar" ]; then
    sercorpusprefix=cwar
else
    sercorpusprefix=trf
fi
if [ $corpusname == "cwar" ]; then
    sercorpussuffix="-20spd"
else
    sercorpussuffix=""
fi
sercorpusfile=$sercorpusprefix$split-$topidmethod-g1dpc$sercorpussuffix.ser.gz;
corpusdir=${4%/}/$split/; # fourth argument is path to corpus in XML format
if [ $corpusname == "cwar" ]; then
    logfileprefix=cwar
else
    logfileprefix=trconll
fi
if [ $corpusname == "cwar" ]; then
    logfilesuffix="-20spd"
else
    logfilesuffix=""
fi

logfile=enwiki-$logfileprefix$split$logfilesuffix-100.log;

mem=8g;

function prettyprint {
    if [ $topidmethod == "ner" ]; then
        echo $1 "&" $2 "&" $3 "&" $4
    else
        echo $1 "&" $2 "&" $3 "&" $4 "&" $5
    fi
}

function getr1 {
    if [ $topidmethod == "ner" ]; then
        echo `grep -A50 "$1" temp-results.txt | grep "P: " | tail -1 | sed -e 's/^.*: //'`
    else
        echo `grep -A50 "$1" temp-results.txt | grep "Mean error distance (km): " | tail -1 | sed -e 's/^.*: //'`
    fi
}

function getr2 {
    if [ $topidmethod == "ner" ]; then
        echo `grep -A50 "$1" temp-results.txt | grep "R: " | tail -1 | sed -e 's/^.*: //'`
    else
        echo `grep -A50 "$1" temp-results.txt | grep "Median error distance (km): " | tail -1 | sed -e 's/^.*: //'`
    fi
}

function getr3 {
    echo `grep -A50 "$1" temp-results.txt | grep "F: " | tail -1 | sed -e 's/^.*: //'`
}

function getr4 {
    echo `grep -A50 "$1" temp-results.txt | grep "Fraction of distances within 161 km: " | tail -1 | sed -e 's/^.*: //'`
}

function printres {

    r1=`getr1 $1`
    r2=`getr2 $2`
    r3=`getr3 $3`
    r4=`getr4 $4`

    prettyprint $1 $r1 $r2 $r3 $r4

}

if [ -e temp-results.txt ]; then
    rm temp-results.txt
fi

echo "\oracle" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -r random -oracle >> temp-results.txt
printres "\oracle"

r1=""
r2=""
r3=""
r4=""
for i in 1 2 3
do
  echo "\rand"$i >> temp-results.txt
  fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -r random >> temp-results.txt
  r1+=`getr1 "\rand$i"`" "
  r2+=`getr2 "\rand$i"`" "
  r3+=`getr3 "\rand$i"`" "
  r4+=`getr4 "\rand$i"`" "
done
r1=`fieldspring run opennlp.fieldspring.tr.util.Average $r1`
r2=`fieldspring run opennlp.fieldspring.tr.util.Average $r2`
r3=`fieldspring run opennlp.fieldspring.tr.util.Average $r3`
r4=`fieldspring run opennlp.fieldspring.tr.util.Average $r4`
prettyprint "\rand" $r1 $r2 $r3 $r4

echo "\population" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -r pop >> temp-results.txt
printres "\population"

r1=""
r2=""
r3=""
r4=""
for i in 1 2 3
do
  echo "bmd"$i >> temp-results.txt
  fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -r wmd -it 1 >> temp-results.txt
  r1+=`getr1 "bmd$i"`" "
  r2+=`getr2 "bmd$i"`" "
  r3+=`getr3 "bmd$i"`" "
  r4+=`getr4 "bmd$i"`" "
done
r1=`fieldspring run opennlp.fieldspring.tr.util.Average $r1`
r2=`fieldspring run opennlp.fieldspring.tr.util.Average $r2`
r3=`fieldspring run opennlp.fieldspring.tr.util.Average $r3`
r4=`fieldspring run opennlp.fieldspring.tr.util.Average $r4`
echo -n "\\"
prettyprint "bmd" $r1 $r2 $r3 $r4

r1=""
r2=""
r3=""
r4=""
for i in 1 2 3
do
  echo "\spider"$i >> temp-results.txt
  fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -r wmd -it 10 >> temp-results.txt
  r1+=`getr1 "\spider$i"`" "
  r2+=`getr2 "\spider$i"`" "
  r3+=`getr3 "\spider$i"`" "
  r4+=`getr4 "\spider$i"`" "
done
r1=`fieldspring run opennlp.fieldspring.tr.util.Average $r1`
r2=`fieldspring run opennlp.fieldspring.tr.util.Average $r2`
r3=`fieldspring run opennlp.fieldspring.tr.util.Average $r3`
r4=`fieldspring run opennlp.fieldspring.tr.util.Average $r4`
prettyprint "\spider" $r1 $r2 $r3 $r4

echo "\tripdl" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r prob -pdg >> temp-results.txt
printres "\tripdl"

echo "\wistr" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r maxent >> temp-results.txt
printres "\wistr"

echo "Necessary for next step:" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r prob -pme >> temp-results.txt

echo "\wistr+\spider" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r wmd -it 10 -rwf >> temp-results.txt
r1=`getr1 "\wistr+"`
r2=`getr2 "\wistr+"`
r3=`getr3 "\wistr+"`
r4=`getr4 "\wistr+"`
prettyprint "\wistr+\spider" $r1 $r2 $r3 $r4

echo "\trawl" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r prob >> temp-results.txt
printres "\trawl"

echo "\trawl+\spider" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -l $logfile -r wmd -it 10 -rwf >> temp-results.txt
r1=`getr1 "\trawl+"`
r2=`getr2 "\trawl+"`
r3=`getr3 "\trawl+"`
r4=`getr4 "\trawl+"`
prettyprint "\trawl+\spider" $r1 $r2 $r3 $r4

#echo "TextConstructTPPGrid" >> temp-results.txt
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -r constructiontpp -dpc 10 >> temp-results.txt
#printres "TextConstructTPPGrid"

#echo "TextConstructTPPCluster" >> temp-results.txt
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -r constructiontpp -t 250 >> temp-results.txt
#printres "TextConstructTPPCluster"

#echo "TextACOTPPGrid" >> temp-results.txt
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -r acotpp -dpc 10 >> temp-results.txt
#printres "TextACOTPPGrid"

#echo "TextACOTPPCluster" >> temp-results.txt
#fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $modelsdir -r acotpp -t 250 >> temp-results.txt
#printres "TextACOTPPCluster"

echo "\listr" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $listrmodelsdir -l $logfile -r maxent >> temp-results.txt
printres "\listr"

echo "\wistr+\listr$_{Mix}$" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $wistrlistrmodelsdir -l $logfile -r maxent >> temp-results.txt
r1=`getr1 "Mix"`
r2=`getr2 "Mix"`
r3=`getr3 "Mix"`
r4=`getr4 "Mix"`
prettyprint "\wistr+\listr{Mix}" $r1 $r2 $r3 $r4


echo "\wistr+\listr$_{Boff}$" >> temp-results.txt
fieldspring --memory $mem resolve -i $corpusdir -sci $sercorpusfile -cf tr -im $listrmodelsdir:$modelsdir -l $logfile -r maxent >> temp-results.txt
r1=`getr1 "Boff"`
r2=`getr2 "Boff"`
r3=`getr3 "Boff"`
r4=`getr4 "Boff"`
prettyprint "\wistr+\listr{Backoff}" $r1 $r2 $r3 $r4

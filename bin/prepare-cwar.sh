#!/bin/bash

if [ -z $FIELDSPRING_DIR ]; then
    echo "You must set the environment variable FIELDSPRING_DIR to point to Fieldspring's installation directory."
    exit
fi

origcwarxmldir=${1%/}
pathtokml=$2
pathtogaz=$3
cwarxmloutdir=${4%/}

echo "Converting original Cwar corpus to plain format..."
cwarxml2txttgn.sh $origcwarxmldir cwarplaintgn
echo "Splitting corpus into dev and test sets..."
fieldspring --memory 2g run opennlp.fieldspring.tr.app.SplitDevTest cwarplaintgn
if [ ! -e $cwarxmloutdir ]; then
    mkdir $cwarxmloutdir
fi
if [ ! -e $cwarxmloutdir/dev ]; then
    mkdir $cwarxmloutdir/dev
fi
if [ ! -e $cwarxmloutdir/test ]; then
    mkdir $cwarxmloutdir/test
fi

echo "Converting dev corpus to Fieldspring format..."
fieldspring --memory 8g run opennlp.fieldspring.tr.app.ConvertCwarToGoldCorpus cwarplaintgndev $pathtokml $pathtogaz > $cwarxmloutdir/dev/cwar-dev.xml
echo "Converting test corpus to Fieldspring format..."
fieldspring --memory 8g run opennlp.fieldspring.tr.app.ConvertCwarToGoldCorpus cwarplaintgntest $pathtokml $pathtogaz > $cwarxmloutdir/test/cwar-test.xml

echo "Deleting temporary files..."
rm -rf cwarplaintgn
rm -rf cwarplaintgndev
rm -rf cwarplaintgntest
echo "Done."


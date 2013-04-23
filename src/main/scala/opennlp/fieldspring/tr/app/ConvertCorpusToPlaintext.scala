package opennlp.fieldspring.tr.app

import java.io._

import opennlp.fieldspring.tr.topo._
import opennlp.fieldspring.tr.text._
import opennlp.fieldspring.tr.text.prep._
import opennlp.fieldspring.tr.text.io._

import scala.collection.JavaConversions._

object ConvertCorpusToPlaintext extends App {

  val outDirName = if(args(1).endsWith("/")) args(1) else args(1)+"/"
  val outDir = new File(outDirName)
  if(!outDir.exists)
    outDir.mkdir

  val tokenizer = new OpenNLPTokenizer

  val corpus = Corpus.createStoredCorpus
  corpus.addSource(new TrXMLDirSource(new File(args(0)), tokenizer))
  corpus.setFormat(BaseApp.CORPUS_FORMAT.TRCONLL)
  corpus.load

  for(doc <- corpus) {
    val out = new BufferedWriter(new FileWriter(outDirName+doc.getId+".txt"))
    for(sent <- doc) {
      for(token <- sent) {
        out.write(token.getForm+" ")
      }
      out.write("\n")
    }
    out.close
  }
}

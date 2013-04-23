package opennlp.fieldspring.tr.resolver

import opennlp.fieldspring.tr.text._
import opennlp.fieldspring.tr.topo._
import opennlp.fieldspring.tr.util._

import scala.collection.JavaConversions._

class PopulationResolver extends Resolver {

  def disambiguate(corpus:StoredCorpus): StoredCorpus = {

    val rand = new scala.util.Random

    for(doc <- corpus) {
      for(sent <- doc) {
        for(toponym <- sent.getToponyms.filter(_.getAmbiguity > 0)) {
          val maxPopLocPair = toponym.getCandidates.zipWithIndex.maxBy(_._1.getPopulation)
          if(maxPopLocPair._1.getPopulation > 0)
            toponym.setSelectedIdx(maxPopLocPair._2)
          else
            toponym.setSelectedIdx(rand.nextInt(toponym.getAmbiguity)) // back off to random
        }
      }
    }

    corpus
  }
}

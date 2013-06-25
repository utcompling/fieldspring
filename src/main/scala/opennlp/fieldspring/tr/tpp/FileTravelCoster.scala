package opennlp.fieldspring.tr.tpp

import opennlp.fieldspring.tr.util._
import opennlp.fieldspring.tr.text._

import java.io._
import java.util.ArrayList
import scala.collection.JavaConversions._

class FileTravelCoster(val inputFile:String, val corpus:StoredCorpus, val dpc:Double) extends TravelCoster {

  val gaussianTC = new GaussianTravelCoster

  val relevantMarkets = new scala.collection.mutable.HashSet[Int]

  for(doc <- corpus) {
    for(sent <- doc) {
      for(toponym <- sent.getToponyms) {
        for(loc <- toponym.getCandidates) {
          //for(coord <- loc.getRegion.getRepresentatives) {
          relevantMarkets.add(TopoUtil.getCellNumber(loc.getRegion.getCenter, dpc))
          //}
        }
      }
    }
  }

  val probs = new scala.collection.mutable.HashMap[Int, scala.collection.mutable.HashMap[Int, Double]]
  val costs = new scala.collection.mutable.HashMap[(Int, Int), Double]

  val in = new DataInputStream(new FileInputStream(inputFile))

  try {
    while(true) {
      val id1 = in.readInt
      val id2 = in.readInt
      val prob = in.readDouble

      if(relevantMarkets.contains(id1) && relevantMarkets.contains(id2)) {
        val destinations = probs.getOrElse(id1, new scala.collection.mutable.HashMap[Int, Double])
        destinations.put(id2, prob)
        probs.put(id1, destinations)
      }
    }
  } catch {
    case e:Exception => 
  }

  in.close

  for((id1, destinations) <- probs) {
    val total = destinations.map(_._2).sum
    for((id2, cost) <- destinations) {
      costs.put((id1, id2), 1.0-cost/total)
    }
  }

  probs.clear

  println("Read "+costs.size+" relevant probabilities.")

  def apply(m1:Market, m2:Market): Double = {
    //if(costs.contains((m1.id, m2.id))) println("Returned cost of "+costs((m1.id, m2.id))+" from file.")
    //else println("Return default cost of "+gaussianTC(m1, m2))
    costs.getOrElse((m1.id, m2.id), gaussianTC(m1, m2))
  }
}

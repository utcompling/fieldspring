package opennlp.fieldspring.tr.tpp

import java.io._
import java.util.ArrayList

import opennlp.fieldspring.tr.text._
import opennlp.fieldspring.tr.topo._
import opennlp.fieldspring.tr.topo.gaz._
import opennlp.fieldspring.preprocess._

import scala.collection.JavaConversions._

class LinkTravelCoster(articleInfoFile:String, linkFile:String, gnGaz:GeoNamesGazetteer) extends TravelCoster {

  val gaussianTC = new GaussianTravelCoster

  val articleInfos = new scala.collection.mutable.HashMap[String, ArrayList[(Int, Coordinate)]]

  println("Reading article info from "+articleInfoFile+" ...")
  for(line <- scala.io.Source.fromFile(articleInfoFile).getLines) {
    val tokens = line.split("\t")
    val id = tokens(0).toInt
    val nameRaw = tokens(1)
    val looseLookupName = ExtractLinksFromWikiDump.looseLookup(gnGaz, nameRaw)._1
    //if(looseLookupName != null)
    //  println(nameRaw + " --> " + looseLookupName)
    val name = (if(looseLookupName != null && looseLookupName.trim.length > 0) looseLookupName else nameRaw).toLowerCase
    val coordPair = tokens(2).split(",")
    val lat = coordPair(0).toDouble
    val lon = coordPair(1).toDouble
    val infos = articleInfos.getOrElse(name, new ArrayList[(Int, Coordinate)])
    infos.add((id, Coordinate.fromDegrees(lat, lon)))
    articleInfos.put(name, infos)
  }

  val links = new scala.collection.mutable.HashMap[Int, scala.collection.mutable.HashMap[Int, Int]] // location1.id => (location2.id => count)

  println("Reading links from "+linkFile+" ...")
  val in = new DataInputStream(new FileInputStream(linkFile))

  try {
    while(true) {
      val id1 = in.readInt
      val id2 = in.readInt
      val count = in.readInt

      val destinations = links.getOrElse(id1, new scala.collection.mutable.HashMap[Int, Int])

      destinations.put(id2, count)
      links.put(id1, destinations)
    }
  } catch {
    case e:Exception => 
  }

  in.close

  val docIdsToDestinations = new scala.collection.mutable.HashMap[String, scala.collection.mutable.HashSet[Int]]

  // This must be called each time a new document is loaded
  override def setDoc(doc:Document[StoredToken]) {
    super.setDoc(doc)

    var allDestinations = docIdsToDestinations.getOrElse(doc.getId, null)

    if(allDestinations == null) {
      allDestinations = new scala.collection.mutable.HashSet[Int]
      for(sent <- doc) {
        for(toponym <- sent.getToponyms) {
          for(loc <- toponym.getCandidates) {
            val infos = LinkTravelCoster.lookupInfos(articleInfos, loc.getName.toLowerCase)
            if(infos != null) {
              val closestMatch = infos.minBy(p => loc.distance(p._2))
              if(loc.distance(closestMatch._2) <= loc.getThreshold/6372.8)
                allDestinations.add(closestMatch._1)
            }
          }
        }
      }
      docIdsToDestinations.put(doc.getId, allDestinations)
    }

    //println("Loaded "+allDestinations.size+" for doc "+doc.getId)
  }


  val costTable = new scala.collection.mutable.HashMap[(String, Int, Int), Double]

  def apply(m1:Market, m2:Market): Double = {

    if(doc != null && costTable.contains((doc.getId, m1.id, m2.id)))
      costTable((doc.getId, m1.id, m2.id))

    else {

    var num = 0
    var denom = 0

    var destinations = new scala.collection.mutable.HashSet[Int]
    for(loc <- m2.locations.map(p => p._2.loc)) {
      var infos = LinkTravelCoster.lookupInfos(articleInfos, loc.getName.toLowerCase)//.getOrElse(loc.getName.toLowerCase, null)
      if(infos != null) {
        //println("Found "+loc.getName.toLowerCase)
        val closestMatch = infos.minBy(p => loc.distance(p._2))
        //println("closestMatch: "+closestMatch._1+", "+closestMatch._2)
        //println("distance from "+loc.getName+" to "+closestMatch._2+" = "+loc.distance(closestMatch._2))
        //println("threshold = "+loc.getThreshold/6372.8)
        if(loc.distance(closestMatch._2) <= loc.getThreshold/6372.8) {
          //println("Added destination for "+loc.getName+": "+closestMatch._1)
          destinations.add(closestMatch._1)
        }
      }
      //else
      //  println("Nothing for "+loc.getName.toLowerCase)
    }

    for(loc <- m1.locations.map(p => p._2.loc)) {
      //val infos = articleInfos.getOrElse(loc.getName.toLowerCase, null)
      var infos = LinkTravelCoster.lookupInfos(articleInfos, loc.getName.toLowerCase)
      if(infos != null) {
        //println("Found "+loc.getName.toLowerCase)
        val closestMatch = infos.minBy(p => loc.distance(p._2))
        if(loc.distance(closestMatch._2) <= loc.getThreshold/6372.8 && links.contains(closestMatch._1)) {
          //println("Found source for "+loc.getName+": "+closestMatch._1)

          for((dest, count) <- links(closestMatch._1)) {
            if(destinations(dest)) {
              num += count
              //println("  "+count+" links to "+dest+" in second market")
            }
            if((docIdsToDestinations(doc.getId))(dest))
              denom += count
            //println("  "+count+" links elsewhere ")
          }
        }
      }
      //else
      //  println("Nothing for "+loc.getName.toLowerCase)

    }

    val cost = if(denom > 0) (1.0 - num.toDouble/denom) else gaussianTC(m1, m2)

    //if(denom > 0) println("1.0 - "+num+" / "+denom+" = "+cost)
    //else println("denom was 0")

    if(doc != null)
      costTable.put((doc.getId, m1.id, m2.id), cost)

    cost

    }
  }
}

object LinkTravelCoster {
  def lookupInfos(articleInfos:scala.collection.mutable.HashMap[String, ArrayList[(Int, Coordinate)]], locName:String): ArrayList[(Int, Coordinate)] = {
    var infos = articleInfos.getOrElse(locName, null)

    if(infos == null) {
      if(locName.equals("russian federation"))
        infos = articleInfos.getOrElse("russia", null)
      else if(locName.equals("argentine republic"))
        infos = articleInfos.getOrElse("argentina", null)
      else if(locName.equals("united kingdom of great britain and northern ireland"))
        infos = articleInfos.getOrElse("united kingdom", null)
      else {
        val ofIndex = locName.indexOf(" of ")
        if(ofIndex >= 0 && ofIndex+4 < locName.length) {
          //println(locName + " --> " + locName.drop(ofIndex+4).trim)
          infos = articleInfos.getOrElse(locName.drop(ofIndex+4).trim, null)
        }
      }
    }

    infos
  }
}

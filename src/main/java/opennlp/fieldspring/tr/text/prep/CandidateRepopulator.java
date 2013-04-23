package opennlp.fieldspring.tr.text.prep;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.fieldspring.tr.text.Corpus;
import opennlp.fieldspring.tr.text.Document;
import opennlp.fieldspring.tr.text.DocumentSource;
import opennlp.fieldspring.tr.text.DocumentSourceWrapper;
import opennlp.fieldspring.tr.text.Sentence;
import opennlp.fieldspring.tr.text.SimpleSentence;
import opennlp.fieldspring.tr.text.SimpleToponym;
import opennlp.fieldspring.tr.text.Token;
import opennlp.fieldspring.tr.text.Toponym;
import opennlp.fieldspring.tr.topo.gaz.Gazetteer;
import opennlp.fieldspring.tr.topo.Location;
import opennlp.fieldspring.tr.util.Span;


public class CandidateRepopulator extends DocumentSourceWrapper {

  private final Gazetteer gazetteer;

    public CandidateRepopulator(DocumentSource source, Gazetteer gazetteer) {
    super(source);
    this.gazetteer = gazetteer;
  }

  public Document<Token> next() {
    final Document<Token> document = this.getSource().next();
    final Iterator<Sentence<Token>> sentences = document.iterator();

    return new Document<Token>(document.getId()) {
      private static final long serialVersionUID = 42L;
      public Iterator<Sentence<Token>> iterator() {
        return new SentenceIterator() {
          public boolean hasNext() {
            return sentences.hasNext();
          }

          public Sentence<Token> next() {
            Sentence<Token> sentence = sentences.next();
            for(Token token : sentence) {
                if(token.isToponym()) {
                    Toponym toponym = (Toponym) token;
                    List<Location> candidates = gazetteer.lookup(toponym.getForm());
                    if(candidates == null) candidates = new ArrayList<Location>();
                    toponym.setCandidates(candidates);
                    toponym.setGoldIdx(-1);
                }
            }
            return sentence;
            //return new SimpleSentence(sentence.getId(), sentence.getTokens());
          }
        };
      }
    };
  }
}


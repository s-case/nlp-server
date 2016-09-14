package uk.ac.ed.inf.srl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashSet;

import uk.ac.ed.inf.srl.ExperimentalParser;
import uk.ac.ed.inf.srl.ExperimentalParser.*;

import uk.ac.ed.inf.srl.corpus.Predicate;
import uk.ac.ed.inf.srl.corpus.Sentence;
import uk.ac.ed.inf.srl.corpus.Word;
import uk.ac.ed.inf.srl.options.CompletePipelineCMDLineOptions;
import uk.ac.ed.inf.srl.options.FullPipelineOptions;
import uk.ac.ed.inf.srl.rdf.RDF;
import uk.ac.ed.inf.srl.rdf.SentenceRDF;
import uk.ac.ed.inf.srl.util.SemLink;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.rdf.model.AnonId;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.RDFVisitor;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;


public class StaticPipeline
{
	private static CompletePipelineCMDLineOptions options = new CompletePipelineCMDLineOptions(new String[]{
    		"eng",
    		"-tokenize",
    		"-lemma", "../scase-wp3-nlp-parser/models/lemma-train-eng.model",
    		"-tagger", "../scase-wp3-nlp-parser/models/tagger-train-eng.model",
    		"-parser", "../scase-wp3-nlp-parser/models/parse-train-eng.model",
    		"-srl", "../scase-wp3-nlp-parser/models/s-case.model",
    		"-printANN",
	});
    private static CompletePipeline pipeline = getCompletePipeline(options);

    static int tnum;
    static int rnum;
    static Map<Word, String> word2id;
    static Resource rdf_p;
    
    public static void clear() {
        tnum = 1;
        rnum = 1;
        rdf_p = null;
        word2id = new HashMap<Word, String>();
        pipeline.pp.resetStartPosition();
    }
    
    public static CompletePipeline getCompletePipeline(FullPipelineOptions options)
    {
    	CompletePipeline pipeline = null;
    	try {
    		pipeline = CompletePipeline.getCompletePipeline(options);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
        return pipeline;
    }

	public static JSONArray parseSentenceANN(String string) {
		return parse2ANN(string);
	}
	
	public static JSONArray parseSentenceANN2(String string) {
		return parse2ANN2(string);
	}
	
	public static JSONObject parseSentenceTTL(String sen) {
		return parse2TTL(sen, new RDF(), "");
	}
	
	public static void newProject(String projectname) {
		RDF rdf = new RDF();
		rdf_p = rdf.createResource(rdf.o_nms + projectname);
	}
	public static JSONObject getProject() {
		JSONObject anno = new JSONObject();
		if(rdf_p!=null)
			addResource(rdf_p, anno);
		return anno;
	}
	
	public static JSONObject parseSentenceTTL(String sen, String sentID) {		
		return parse2TTL(sen, new RDF(), sentID);
	}
	
	public static JSONObject parse2TTL(String sen, RDF rdf, String sentID) {
		JSONObject anno = new JSONObject();
        Resource rdf_r = rdf.createResource(rdf.o_nms + sentID);
        rdf_r.addProperty(rdf.TYPE, rdf.o_nms + "FunctionalRequirement");
        
        SentenceRDF rdfs = null;
		try {
			rdfs = new SentenceRDF(rdf, (SemLink)null, pipeline.parse(sen), sentID);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		for(Resource r : rdfs) {
			if(r==null) continue;
			if(r.hasProperty(rdf.TYPE, "http://www.owl-ontologies.com/Ontology1273059028.owl#action"))
				rdf_r.addProperty(rdf.createProperty(rdf.o_nms + "requirement_has_operation"), r);
				
			addResource(r, anno);
		}
		
		if(!sentID.equals("")) {
			addResource(rdf_r, anno);
			if(rdf_p!=null)
				rdf_p.addProperty(rdf.createProperty(rdf.o_nms + "project_has_requirement"), rdf_r);
		}

		return anno;
	}
	
	private static void addResource(Resource r, JSONObject anno) {
		try {
			Iterator<Statement> iter = r.listProperties();
			JSONObject struct = new JSONObject();
			while(iter.hasNext()) {
				Statement s = iter.next();
				JSONArray values = new JSONArray();
				if(struct.has(s.getPredicate().getURI()))
					values = (JSONArray)struct.get(s.getPredicate().getURI());
				values.put(s.getObject().toString());
				struct.put(s.getPredicate().getURI(), values);
			}
			anno.put(r.getURI(), struct);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static JSONArray parse2ANN(String sen) {
		Map<String, String> id2anno = new TreeMap<String, String>();
		
		try {
			Sentence s = pipeline.parse(sen);
			
			 for (Predicate p : s.getPredicates()) {
	                if (p.getSense().equals("Action") || p.getSense().equals("OPERATION")) {
	                	id2anno.put(id(p), "Action" + " " + p.getBegin() + " " + p.getEnd() + " " + p.getForm());

	                    for (Word w : p.getArgMap().keySet()) {
	                        String label = p.getArgMap().get(w);

	                        if (!word2id.containsKey(w))
	                        	id2anno.put(id(w), label + " " + w.getBegin() + " " + w.getEnd() + " " + w.getForm());

	                        id2anno.put("R" + (rnum++), 
	                        		(label.equals("Actor") ? ("IsActorOf Arg1:" + id(w) + " Arg2:" + id(p))
	                                        : ("ActsOn Arg1:" + id(p) + " Arg2:" + id(w))));
	                    }
	                }

	                if (p.getSense().equals("Object") || p.getSense().equals("CONCEPT")) {
	                    if (!word2id.containsKey(p))
	                    	id2anno.put(id(p), "Object" + " " + p.getBegin() + " " + p.getEnd() + " " + p.getForm());

	                    for (Word w : p.getArgMap().keySet()) {
	                        String label = p.getArgMap().get(w);

	                        if (!word2id.containsKey(w))
	                        	id2anno.put(id(w), label + " " + w.getBegin() + " " + w.getEnd() + " " + w.getForm());

	                        id2anno.put("R" + (rnum++), "HasProperty Arg1:" + id(p) + " Arg2:" + id(w));
	                    }
	                }
	            }
			 
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		JSONArray retval = new JSONArray();
		for(String key : id2anno.keySet()) {
			retval.put(key + " " + id2anno.get(key));
		}
		return retval;
	}

        public static String bratWordSummary(Word w, ExperimentalParser ep, String sen)
        {
	    Word comp = ep.compoundNouns.get(w);
	    if(comp != null)
	    {
		return comp.getBegin() + " " + comp.getEnd() + " " +
		    sen.substring(comp.getBegin(), comp.getEnd());
	    }
	    else
		return w.getBegin() + " " + w.getEnd() + " " + w.getForm();
	}
    
	public static JSONArray parse2ANN2(String sen) {
		Map<String, String> id2anno = new TreeMap<String, String>();
		
		try {
			Sentence s = pipeline.parse(sen);
			ExperimentalParser ep = new ExperimentalParser(s);

			for(Action a : ep.actions)
			{
			    id2anno.put(id(a.word),
					"Action " + bratWordSummary(a.word, ep, sen));
			    for(Actor aa : a.actors)
				id2anno.put("R" + (rnum++),
					    "IsActorOf Arg1:" + id(aa.word) + " Arg2:" + id(a.word));
			    for(Obj aa : a.objects)
				id2anno.put("R" + (rnum++),
					    "ActsOn Arg1:" + id(a.word) + " Arg2:" + id(aa.word));
			}
			
			for(Actor a : ep.actors.values())
			{
			    id2anno.put(id(a.word),
					"Actor " + bratWordSummary(a.word, ep, sen));
			    for(uk.ac.ed.inf.srl.ExperimentalParser.Property aa : a.properties)
				id2anno.put("R" + (rnum++),
					    "HasProperty Arg1:" + id(a.word) + " Arg2:" + id(aa.word));
			}
			
			for(Obj a : ep.objects.values())
			{
			    id2anno.put(id(a.word),
					"Object " + bratWordSummary(a.word, ep, sen));

			    for(uk.ac.ed.inf.srl.ExperimentalParser.Property aa : a.properties)
				id2anno.put("R" + (rnum++),
					    "HasProperty Arg1:" + id(a.word) + " Arg2:" + id(aa.word));
			}

			for(uk.ac.ed.inf.srl.ExperimentalParser.Property a : ep.properties.values())
			    id2anno.put(id(a.word),
					"Property " + bratWordSummary(a.word, ep, sen));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		JSONArray retval = new JSONArray();
		for(String key : id2anno.keySet()) {
			retval.put(key + " " + id2anno.get(key));
		}
		return retval;
	}


    // Simple query term extraction: use the nouns
    
    public static JSONArray findQueryTerms(String question)
    {
	JSONArray terms = new JSONArray();

	try
	{
	    System.err.println("Parsing question: " + question);

	    Sentence s = pipeline.parse(question);

	    for(Word w : s)
	    {
		System.err.println("Word: " + w.getForm() + " " + w.getPOS() + " " + w.getLemma());
		if(w.getPOS().startsWith("NN"))
		    terms.put(w.getLemma());
	    }

	    return terms;
	} 
	catch(Exception e)
	{
	    e.printStackTrace();
	    return terms;
	}
    }

    // Find query terms using annotated sentence

    static HashSet actionstoplist = new HashSet();
    static HashSet actorstoplist = new HashSet();
    static
    {
	actionstoplist.add("be");
	actionstoplist.add("have");
	actionstoplist.add("do");

	actorstoplist.add("you");
	actorstoplist.add("i");
    }

    public static JSONArray findQueryTerms2(String question)
    {
	JSONArray terms = new JSONArray();

	try
	{
	    Sentence s = pipeline.parse(question);
	    ExperimentalParser ep = new ExperimentalParser(s, true);

	    if(ep.actions.size() == 0)
	    {
		System.err.println("falling back to nouns");

		for(Word w : s)
		{
		    // Just find the nouns; ignore NMODs and NAMEs as they
		    // will probably be parts of compound nouns
		    if(w.getPOS().startsWith("NN") &&
		       !w.getDeprel().equals("NMOD") && !w.getDeprel().equals("NAME"))
			terms.put(wordText(w, ep, question));
		}

		return terms;
	    }
	    
	    for(Actor a : ep.actors.values())
	    {
		String text = wordText(a.word, ep, question);
		if(!actorstoplist.contains(text))
		    terms.put(text);
	    }
			
	    for(Action a : ep.actions)
	    {
		String text = wordText(a.word, ep, question);
		if(!actionstoplist.contains(text))
		    terms.put(text);
	    }

	    for(Obj a : ep.objects.values())
		terms.put(wordText(a.word, ep, question));

	    for(uk.ac.ed.inf.srl.ExperimentalParser.Property a : ep.properties.values())
		terms.put(wordText(a.word, ep, question));

	    return terms;
	} 
	catch(Exception e)
	{
	    e.printStackTrace();
	    return terms;
	}
    }

    // Return text of word, allowing for compound words
    // XXX change this to return lemmas
    
    public static String wordText(Word w, ExperimentalParser ep, String sen)
    {
	Word comp = ep.compoundNouns.get(w);
	
	if(comp != null)
	    return comp.getLemma();
	else
	    return w.getLemma();
    }

    private static String id(Word w)
    {
        if (!word2id.containsKey(w))
            word2id.put(w, ("T" + (tnum++)));
        return word2id.get(w);
    }

}

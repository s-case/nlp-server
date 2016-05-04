package uk.ac.ed.inf.srl;

import java.util.Hashtable;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

import java.io.PrintStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import uk.ac.ed.inf.srl.corpus.Predicate;
import uk.ac.ed.inf.srl.corpus.Sentence;
import uk.ac.ed.inf.srl.corpus.Word;
import uk.ac.ed.inf.srl.options.CompletePipelineCMDLineOptions;
import uk.ac.ed.inf.srl.options.FullPipelineOptions;
import uk.ac.ed.inf.srl.rdf.RDF;
import uk.ac.ed.inf.srl.rdf.SentenceRDF;
import uk.ac.ed.inf.srl.util.SemLink;

public class ExperimentalParser
{
    Sentence sentence;
    Word root;
    HashSet<Action> actions = new HashSet<Action>();
    Hashtable<Word, Actor> actors = new Hashtable<Word, Actor>();
    Hashtable<Word, Obj> objects = new Hashtable<Word, Obj>();
    Hashtable<Word, Property> properties = new Hashtable<Word, Property>();
    Hashtable<Word, HashSet<Word>> conjunctions = new Hashtable<Word, HashSet<Word>>();
    Hashtable<Word, Word> compoundNouns = new Hashtable<Word, Word>();
    
    public ExperimentalParser(Sentence s)
    {
	this(s, false);
    }
    
    public ExperimentalParser(Sentence s, boolean is_question)
    {
	sentence = s;

	printDependencies(System.err);
	
	Set<Word> roots = s.get(0).getChildren();
	assert roots.size() == 1;

	// XXX is there a better way to get the known single element?
	for(Word r : roots)
	    root = r;

//	System.err.println("root is " + root);

	if(is_question)
	    skipImperatives();

	findCompoundNouns();
	
	findConjunctions();
	
	findActions();

	for(Action a : actions)
	    findActors(a);

	for(Action a : actions)
	    findObjects(a);
	for(Action a : actions)
	    findMissingObjects(a);

	for(Action a : actions)
	{
	    for(Thing t : a.objects)
		findObjectProperties(t, a);
	    if(a.objects.size() == 0)
		findObjectProperties(null, a);
	}
	
	System.err.println("actions are " + actions);
    }

    // For questions, if the root seems to be an imperative like
    // "find" or "show", replace it with a descendant verb if
    // possible.  E.g. for "Find systems which can ..." use "can".

    void skipImperatives()
    {
	// See if the root has a subject.  If it has, assume
	// it's not an imperative.

	// By happy coincidence, this will handle cases where the root
	// is not a verb.  XXX maybe handle explicitly.

	for(Word w : sentence)
	    if(w.getDeprel().equals("SBJ") && w.getHead() == root)
		return;

	// It seems to be an imperative, look for a descendant verb
	
	List<Word> verbs = findMatchingDescendants(root,
						   new WordTest() {
						       public boolean test(Word w) {
							   return isActiveVerb(w) ||
							       w.getPOS().equals("MD");}});
	if(verbs.size() >= 1)
	{
	    // XXX what if there are several?
	    root = verbs.get(0);
	    System.err.println("switching root to " + root.getForm());
	}
    }

    // Find compound nouns such as "CPU power" and "Virtual Machine"

    void findCompoundNouns()
    {
	int i, j, k;

	// work backwards through the sentence so we don't treat a modifier
	// as the potential base of a compound noun

	for(i=sentence.size()-1; i>=0; --i)
	{
	    // the base must be a noun
	    
	    Word base = sentence.get(i);
	    if(!base.getPOS().startsWith("NN"))
		continue;

	    // look at preceeding words to see if they form a compound
	    // noun with the base
	    
	    for(j=i-1; j >= 0 && isCompoundNoun(sentence.get(j), base); --j)
		;
	    j++;

	    // if we found a compound noun, make a fake word object for it
	    
	    if(j < i)
	    {
		StringBuffer buf = new StringBuffer();
		for(k=j; k<i; k++)
		{
		    buf.append(sentence.get(k).getForm());
		    buf.append(" "); // XXX whitespace may be wrong
		}
		buf.append(sentence.get(i).getForm());
		Word compound = new Word(buf.toString(), null, null, null, null, 0);
		compound.setBegin(sentence.get(j).getBegin());
		compound.setEnd(base.getEnd());
		for(k=j; k<=i; k++)
		    compoundNouns.put(sentence.get(k), compound);
		System.err.println("compound " + compound.getForm());
	    }

	    i = j;		// skip over the words we've consumed
	}
    }

    boolean isCompoundNoun(Word mod, Word base)
    {
	// noun modifying noun is compound

	if(mod.getPOS().startsWith("NN") && mod.getDeprel().equals("NMOD"))
	    return true;

	// name relation is compound
	
	if(mod.getDeprel().equals("NAME"))
	    return true;

	// various known modifiers

//	System.err.println("considering " + mod.getForm() + " " + mod.getDeprel() + " " + base.getForm());
	
	if(mod.getDeprel().equals("NMOD"))
	{
	    if(mod.getForm().equalsIgnoreCase("virtual"))
		return true;
	}

	return false;
    }
	   
    
    // Find conjunctions and group conjuncts in sets
    
    void findConjunctions()
    {
	HashSet conjuncts;
	Word target;

	for(Word w : sentence)
	{
	    if(w.getPOS().equals("CC"))
	    {
		conjuncts = conjunctions.get(w);
		if(conjuncts == null)
		{
		    conjuncts = new HashSet<Word>();
		    conjuncts.add(w);
		    conjunctions.put(w, conjuncts);
		}

		continue;
	    }		
	    
	    for(target = w;
		target.getDeprel().equals("COORD") || target.getDeprel().equals("CONJ");
		target = target.getHead())
		;
	    
	    conjuncts = conjunctions.get(target);
	    if(conjuncts == null)
	    {
		conjuncts = new HashSet<Word>();
		conjuncts.add(w);
		conjuncts.add(target);
		conjunctions.put(w, conjuncts);
		conjunctions.put(target, conjuncts);
	    }
	    else
	    {
		conjuncts.add(w);
		conjunctions.put(w, conjuncts);
	    }
	}

	/*
	System.err.println("Conjunctions:");
	for(Word w : sentence)
	{
	    System.err.print(w.getForm() + ": ");
	    for(Word w2 : conjunctions.get(w))
		System.err.print(w2.getForm() + " ");
	    System.err.println("");
	}
	*/
    }
    
    void findActions()
    {
	// If the root is a plain verb it's the action.
	// If it's a modal verb or auxillary use its deepest plain verb descendant.
	// Any conjuncts of the action are also actions.
	
	if(!(root.getPOS().startsWith("VB") || root.getPOS().equals("MD")))
	    // can't find action
	    return;

	Set<Word> roots = conjunctions.get(root);

	for(Word r : roots)
	{
	    if(r.getPOS().startsWith("VB") && !isAuxillary(r))
		actions.add(new Action(r));
	    else // MD or auxillary
	    {
		List<Word> verbs = findMatchingDescendants(root,
							   new WordTest() {
							       public boolean test(Word w) {
								   return w.getPOS().equals("VB");}});
		if(verbs.size() >= 1)
		{
		    Word verb = verbs.get(verbs.size() - 1);
		    ArrayList<Word> ancestorVerbs = new ArrayList<Word>();

		    for(Word w = verb.getHead(); w != null; w = w.getHead())
			if(w.getPOS().startsWith("VB") || w.getPOS().equals("MD"))
			    ancestorVerbs.add(w);
		    for(Word w : conjunctions.get(verb))
			actions.add(new Action(w, root, ancestorVerbs));
		    
		}
	    }
	}
    }

    boolean isAuxillary(Word w)
    {
	// Must be is/do/have and have a complement (VC)
	
	if(!w.getLemma().equals("be") &&
	   !w.getLemma().equals("do") &&
	   !w.getLemma().equals("have"))
	    return false;

	for(Word w2 : sentence)
	    if(w2.getDeprel().equals("VC") && w2.getHead() == w)
		return true;

	return false;
    }

    boolean isActiveVerb(Word w)
    {
	return w.getPOS().equals("VB") || w.getPOS().equals("VBP") ||
	    w.getPOS().equals("VBZ") || w.getPOS().equals("VBD");
    }

    void findActors(Action action)
    {
	Word verb = (action.modal == null ? action.word : action.modal);
	Word subject = null;

	
	for(Word w : sentence)
	    // XXX should we only accept nouns?
	    if(w.getDeprel().equals("SBJ") && w.getHead() == verb && !w.getPOS().equals("WDT"))
	    {
		subject = w;
		break;
	    }
	
	if(subject == null)
	    return;
	
	for(Word s : conjunctions.get(subject))
	    action.actors.add(getActor(s));
    }
    
    void findObjects(Action action)
    {
	Word verb = action.word;
	Word object = null;

	
	for(Word w : sentence)
	    if(w.getDeprel().equals("OBJ") && w.getHead() == verb)
	    {
		object = w;
		break;
	    }
	
	if(object == null)
	    return;
	
	for(Word o : conjunctions.get(object))
	    action.objects.add(getObject(o));
    }

    void findMissingObjects(Action action)
    {
	// if an action doesn't have any objects, use another actions's
	// (e.g. "create and delete an account")

	if(action.objects.size() != 0)
	    return;

	for(Action a : actions)
	    if(a.objects.size() != 0)
	    {
		action.objects.addAll(a.objects);
		return;
	    }
    }

    void findObjectProperties(Thing t, Action a)
    {
	// Look for words and phrases modifying the object
	// and phrases modifying the action.
	// If t is null the action has no objects, so leave
	// any properties unattached

	ArrayList<Word> phraseHeads = new ArrayList<Word>();

	if(t != null)
	{
	    for(Word w : sentence)
		if(w.getHead() == t.word &&
		   (w.getDeprel().equals("NMOD") || w.getDeprel().equals("APPO")))
		{
		    if(w.getPOS().equals("DT"))
			;
		    if(compoundNouns.get(w) != null)
			// it's a compound nound, not a property
			;
		    else if(w.getPOS().startsWith("JJ") || w.getPOS().startsWith("NN") ||
			    w.getPOS().equals("VBG"))
		    {
			t.properties.add(getProperty(w));
		    }
		    else
		    {
			phraseHeads.add(w);
		    }
		}
	}
	
	for(Word w : sentence)
	    if((w.getHead() == a.word || a.ancestorVerbs.contains(w.getHead())) &&
	       (w.getDeprel().equals("ADV") || w.getDeprel().equals("LOC") ||
		w.getDeprel().equals("DIR") || w.getDeprel().equals("MNR") ||
		w.getDeprel().equals("PRP") || w.getDeprel().equals("EXT")))
	    {
		phraseHeads.add(w);
	    }

	// Find properties in the phrases
	
	for(Word phraseHead : phraseHeads)
	{
	    // Use the highest noun in the phrase, and its conjuncts

	    List<Word> nouns = findMatchingDescendants(phraseHead,
						       new WordTest() {
							   public boolean test(Word w) {
							       return w.getPOS().startsWith("NN");}});
	    if(nouns.size() > 0)
	    {
		Set<Word> pnouns = conjunctions.get(nouns.get(0));
		for(Word w : pnouns)
		{
		    Property p = getProperty(w);
		    if(t != null)
			t.properties.add(p);
		}
	    }
	}
    }

    interface WordTest
    {
	boolean test(Word w);
    }

    // Breadth-first search of descendants
    
    ArrayList<Word> findMatchingDescendants(Word start, WordTest pred)
    {
	ArrayList<Word> list = new ArrayList<Word>();
	list.add(start);
	return findMatchingDescendants(list, pred);
    }

    ArrayList<Word> findMatchingDescendants(List<Word> starts, WordTest pred)
    {
	ArrayList<Word> children = new ArrayList<Word>();
	ArrayList<Word> matches = new ArrayList<Word>();

	if(starts.isEmpty())
	    return matches;
	
	// Get a list of all the children
	
	for(Word start : starts)
	{
	    for(Word c : start.getChildren())
		children.add(c);
	}

	// Add the matching ones
	
	for(Word c : children)
	    if(pred.test(c))
		matches.add(c);

	// Add the matching descendants of the children

	matches.addAll(findMatchingDescendants(children, pred));

	return matches;
    }

    class Action
    {
	Word word, modal;
	ArrayList<Word> ancestorVerbs;
	
	List<Actor> actors = new ArrayList<Actor>();
	List<Obj> objects = new ArrayList<Obj>();

	Action(Word word)
	{
	    this.word = word;
	    this.ancestorVerbs = new ArrayList<Word>();
	}

	Action(Word word, Word modal, ArrayList<Word> ancestorVerbs)
	{
	    this.word = word;
	    this.modal = modal;
	    this.ancestorVerbs = ancestorVerbs;
	}

	public String toString()
	{
	    StringBuffer buf = new StringBuffer(word.getForm());
/*
	    if(ancestorVerbs.size() > 0)
		for(Word w : ancestorVerbs)
		    buf.append("/" + w.getForm());
*/
	    return actors + "-" + buf + "-" + objects;
	}
    }

    Obj getObject(Word word)
    {
	Obj t = objects.get(word);

	if(t == null)
	{
	    t = new Obj(word);
	    objects.put(word, t);
	}
	
	return t;
    }
    
    Property getProperty(Word word)
    {
	Property t = properties.get(word);

	if(t == null)
	{
	    t = new Property(word);
	    properties.put(word, t);
	}
	
	return t;
    }
    
    Actor getActor(Word word)
    {
	Actor t = actors.get(word);

	if(t == null)
	{
	    t = new Actor(word);
	    actors.put(word, t);
	}
	
	return t;
    }
    
    abstract class Thing
    {
	Word word;
	HashSet<Property> properties = new HashSet<Property>();

	Thing(Word word)
	{
	    this.word = word;
	}

	public String toString()
	{
	    Word comp = compoundNouns.get(word);
	    String form = (comp == null ? word.getForm() : "{" + comp.getForm() + "}");
	    
	    return form + (word.getPOS().equals("PRP") ? "*" : "") +
		"-" + properties;
	}
    }

    class Obj extends Thing
    {
	Obj(Word word)
	{
	    super(word);
	}
    }

    class Property extends Thing
    {
	Property(Word word)
	{
	    super(word);
	}

	public String toString()
	{
	    Word comp = compoundNouns.get(word);
	    String form = (comp == null ? word.getForm() : "{" + comp.getForm() + "}");
	    
	    return form;
	}
    }

    class Actor extends Thing
    {
	Actor(Word word)
	{
	    super(word);
	}
    }

    void printDependencies(PrintStream out)
    {
	for(int i=1; i<sentence.size(); i++)
	{
	    Word w = sentence.get(i);
	    out.printf("%12s %3s %5s %12s\n", w.getForm(), w.getPOS(), w.getDeprel(), w.getHead().getForm());
	}
    }
    
    private static CompletePipelineCMDLineOptions options = new CompletePipelineCMDLineOptions(new String[]{
	    "eng",
	    "-tokenize",
	    "-lemma", "git/scase-wp3-nlp-parser/models/lemma-train-eng.model",
	    "-tagger", "git/scase-wp3-nlp-parser/models/tagger-train-eng.model",
	    "-parser", "git/scase-wp3-nlp-parser/models/parse-train-eng.model",
	    "-srl", "git/scase-wp3-nlp-parser/models/s-case.model",
	    "-printANN",
	});

    public static void main(String args[])
    {
	Sentence s;
	String line;

	try
	{
	    CompletePipeline pipeline = CompletePipeline.getCompletePipeline(options);
	    
	    if(args.length == 0)
	    {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		while((line = in.readLine()) != null)
		{
		    System.out.println(line);
		    s = pipeline.parse(line);
		    ExperimentalParser r = new ExperimentalParser(s);
		}

	    }
	    else
	    {
		s = pipeline.parse(args[0]);
		ExperimentalParser r = new ExperimentalParser(s);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    return;
	}

    }
    
}

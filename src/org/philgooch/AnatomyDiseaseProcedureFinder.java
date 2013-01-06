/*
 *  Copyright (c) 2012, Phil Gooch.
 *
 *  This software is licenced under the GNU Library General Public License,
 *  http://www.gnu.org/copyleft/gpl.html Version 3, 29 June 2007
 *
 *  Phil Gooch 04/2012
*/


package org.philgooch;

import gate.*;
import gate.creole.*;
import gate.creole.metadata.*;
import gate.util.*;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.event.ProgressListener;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;
import java.io.*;
import java.net.*;

/**
 *
 * @author philipgooch
 */
@CreoleResource(name = "Anatomy, Disease and Procedure Annotator",
helpURL = "",
comment = "Uses neoclassical suffixes and key words to identify candidate anatomy, disease and procedure mentions in the text.")
public class AnatomyDiseaseProcedureFinder extends AbstractLanguageAnalyser implements ProgressListener,
        ProcessingResource,
        Serializable {

    private String inputASName;     //  Input AnnotationSet name
    private String outputASName;    // Output AnnotationSet set name
    private String diseaseType;                   // Annotation for disease mentions
    private String anatomyType;                   // Annotation for anatomy mentions
    private String symptomType;                   // Annotation for symptom mentions
    private String procedureType;                   // Annotation for procedure mentions
    private String testType;                   // Annotation for test mentions
    private Integer minPrefixLength;    // minimum overall length of string before a suffix
    private URL configFileURL;      // URL to configuration file that defines suffixes and key words
    private String sentenceType;               // default to Sentence
    private String nounChunkType;                   // default to NounChunk
    // Map to contain the regex Patterns for disease, anatomy, procedure, medication etc
    private Map<String, Pattern> patternMap;
    // Exit gracefully if exception caught on init()
    private boolean gracefulExit;
    private Transducer japeTransducer = null;     // JAPE to clean up the output
    private URL japeURL;      // URL to JAPE main file

    private Boolean debug;      // output debug info

    /**
     *
     * @param key
     * @param options
     */
    private void addPrefixPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b(" + option + ")"));
        }
    }

    /**
     *
     * @param key
     * @param options
     */
    private void addSuffixPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b(\\w{" + minPrefixLength + ",})(" + option + ")\\b"));
        }
    }

    /**
     *
     * @param key
     * @param options
     */
    private void addSuffixPluralPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b(\\w{" + minPrefixLength + ",})(" + option + ")s?\\b"));
        }
    }


    /**
     *
     * @param key
     * @param options
     */
    private void addWordPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b(" + option + ")\\b"));
        }
    }


    private void addPossessiveWordPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b(" + option + ")['s]{0,2}\\b"));
        }
    }

    /**
     *
     * @param key
     * @param options
     */
    private void addWordPluralPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b(" + option + ")s?\\b"));
        }
    }

    /**
     *
     * @param key
     * @param options
     */
    private void addWordExtraPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b(" + option + ")\\w{0,3}\\b"));
        }
    }


    @Override
    public Resource init() throws ResourceInstantiationException {
        gracefulExit = false;

        if (configFileURL == null) {
            gracefulExit = true;
            gate.util.Err.println("No configuration file provided!");
        }

        if (japeURL == null) {
            gracefulExit = true;
            gate.util.Err.println("No JAPE grammar file provided!");
        }

        // create the init params for the JAPE transducer
        FeatureMap params = Factory.newFeatureMap();
        params.put(Transducer.TRANSD_GRAMMAR_URL_PARAMETER_NAME, japeURL);
        // Code borrowed from Mark Greenwood's Measurements PR
        if (japeTransducer == null) {
            // if this is the first time we are running init then actually create a
            // new transducer as we don't already have one
            FeatureMap hidden = Factory.newFeatureMap();
            Gate.setHiddenAttribute(hidden, true);
            japeTransducer = (Transducer) Factory.createResource("gate.creole.Transducer", params, hidden);
        } else {
            // we are being run through a call to reInit so simply re-init the
            // underlying JAPE transducer
            japeTransducer.setParameterValues(params);
            japeTransducer.reInit();
        }

        ConfigReader config = new ConfigReader(configFileURL);
        gracefulExit = config.config();

        try {
            HashMap<String, String> options = config.getOptions();

            patternMap = new HashMap<String, Pattern>();
            addSuffixPattern("disease_suffix", options);
            addWordPattern("disease_abbrevs", options);
            addWordPattern("disease_sense", options);
            addWordExtraPattern("disease_sense_context", options);
            addPossessiveWordPattern("disease_named_syndrome", options);
            addWordExtraPattern("disease_generic_context", options);
            addWordExtraPattern("disease_anatomy_context", options);
            addSuffixPluralPattern("procedure_suffix", options);
            addWordPluralPattern("procedure_key", options);
            addWordExtraPattern("procedure_anatomy_context", options);
            addWordPluralPattern("symptom_key", options);
            addWordPattern("test_key", options);

            addSuffixPattern("anatomy_suffix_adjective", options);
            addSuffixPattern("anatomy_suffix", options);
            addPrefixPattern("anatomy_prefix", options);
            addWordPattern("anatomy_position", options);
            addWordPluralPattern("anatomy_space_region_junction", options);
            addWordPattern("anatomy_part_adjective", options);
            addWordPattern("anatomy_latin_noun", options);
            addWordPattern("anatomy_muscle", options);
            addWordPluralPattern("anatomy_part", options);
            addWordPluralPattern("anatomy_fluid", options);
            
        } catch (NullPointerException ne) {
            gracefulExit = true;
            gate.util.Err.println("Missing or unset configuration options. Please check configuration file.");
        }

        return this;
    } // end init()

    @Override
    public void execute() throws ExecutionException {
        interrupted = false;

        // quit if setup failed
        if (gracefulExit) {
            gracefulExit("Plugin was not initialised correctly. Exiting gracefully ... ");
            return;
        }

        AnnotationSet inputAS = (inputASName == null || inputASName.trim().length() == 0) ? document.getAnnotations() : document.getAnnotations(inputASName);
        AnnotationSet outputAS = (outputASName == null || outputASName.trim().length() == 0) ? document.getAnnotations() : document.getAnnotations(outputASName);


        AnnotationSet sentenceAS = null;
        if (sentenceType != null && !sentenceType.isEmpty()) {
            sentenceAS = inputAS.get(sentenceType);
        }

        // Document content
        String docContent = document.getContent().toString();
        int docLen = docContent.length();

        // For matching purposes replace all whitespace characters with a single space
        docContent = docContent.replaceAll("[\\s\\xA0\\u2007\\u202F]", " ");

        fireStatusChanged("Locating anatomy, disease and procedure mentions in " + document.getName());
        fireProgressChanged(0);

        if (sentenceAS != null) {
            for (Annotation sentence : sentenceAS) {
                Long sentStartOffset = sentence.getStartNode().getOffset();
                Long sentEndOffset = sentence.getEndNode().getOffset();

                // Converting the sentence to lower case prevents the need to use case-insenstive regex matching, which should give a small performance boost
                String sentenceContent = docContent.substring(sentStartOffset.intValue(), sentEndOffset.intValue()).toLowerCase(Locale.ENGLISH);

                if ( diseaseType != null && ! diseaseType.isEmpty() ) {
                    doMatch(patternMap.get("disease_suffix"), sentenceContent, inputAS, outputAS, "suffDisease", sentStartOffset, docLen);
                    doMatch(patternMap.get("disease_abbrevs"), sentenceContent, inputAS, outputAS, "preDisease", sentStartOffset, docLen);
                    doMatch(patternMap.get("disease_named_syndrome"), sentenceContent, inputAS, outputAS, "namedDisease", sentStartOffset, docLen);
                    doMatch(patternMap.get("disease_sense"), sentenceContent, inputAS, outputAS, "tmpDiseaseSense", sentStartOffset, docLen);
                    doMatch(patternMap.get("disease_sense_context"), sentenceContent, inputAS, outputAS, "tmpDiseaseSenseContext", sentStartOffset, docLen);
                    doMatch(patternMap.get("disease_generic_context"), sentenceContent, inputAS, outputAS, "poDisease", sentStartOffset, docLen);
                    doMatch(patternMap.get("disease_anatomy_context"), sentenceContent, inputAS, outputAS, "tmpDisease", sentStartOffset, docLen);
                }

                if ( procedureType != null && ! procedureType.isEmpty() ) {
                    doMatch(patternMap.get("procedure_suffix"), sentenceContent, inputAS, outputAS, "poProcedure", sentStartOffset, docLen);
                    doMatch(patternMap.get("procedure_key"), sentenceContent, inputAS, outputAS, "poProcedure", sentStartOffset, docLen);
                    doMatch(patternMap.get("procedure_anatomy_context"), sentenceContent, inputAS, outputAS, "tmpProcedure", sentStartOffset, docLen);
                }

                if ( symptomType != null && ! symptomType.isEmpty() ) {
                    doMatch(patternMap.get("symptom_key"), sentenceContent, inputAS, outputAS, "poSymptom", sentStartOffset, docLen);
                }
                
                if ( testType != null && ! testType.isEmpty() ) {
                    doMatch(patternMap.get("test_key"), sentenceContent, inputAS, outputAS, "poTest", sentStartOffset, docLen);
                }

                if ( anatomyType != null && ! anatomyType.isEmpty() ) {
                   doMatch(patternMap.get("anatomy_suffix_adjective"), sentenceContent, inputAS, outputAS, "tmpAnatSuffAdj", sentStartOffset, docLen);
                    doMatch(patternMap.get("anatomy_suffix"), sentenceContent, inputAS, outputAS, "tmpAnatSuff", sentStartOffset, docLen);
                    doMatch(patternMap.get("anatomy_prefix"), sentenceContent, inputAS, outputAS, "tmpAnatPre", sentStartOffset, docLen);
                    doMatch(patternMap.get("anatomy_position"), sentenceContent, inputAS, outputAS, "tmpAnatPos", sentStartOffset, docLen);
                    doMatch(patternMap.get("anatomy_space_region_junction"), sentenceContent, inputAS, outputAS, "tmpAnatSpace", sentStartOffset, docLen);
                    doMatch(patternMap.get("anatomy_part_adjective"), sentenceContent, inputAS, outputAS, "tmpAnatAdj", sentStartOffset, docLen);
                    doMatch(patternMap.get("anatomy_latin_noun"), sentenceContent, inputAS, outputAS, "tmpAnatLatin", sentStartOffset, docLen);
                    doMatch(patternMap.get("anatomy_muscle"), sentenceContent, inputAS, outputAS, "tmpAnatMuscle", sentStartOffset, docLen);
                    doMatch(patternMap.get("anatomy_part"), sentenceContent, inputAS, outputAS, "tmpAnatPart", sentStartOffset, docLen);
                    doMatch(patternMap.get("anatomy_fluid"), sentenceContent, inputAS, outputAS, "tmpAnatFluid", sentStartOffset, docLen);
                }
                
            }
            // Run JAPE transducer to clean up the output
            fireStatusChanged("Processing anatomical, disease and procedure mentions in " + document.getName());
            try {
                japeTransducer.setDocument(document);
                japeTransducer.setInputASName(inputASName);
                japeTransducer.setOutputASName(outputASName);
                japeTransducer.addProgressListener(this);
                japeTransducer.execute();
            } catch (ExecutionException re) {
                gate.util.Err.println("Unable to run " + japeURL);
                gracefulExit = true;
            } finally {
                japeTransducer.setDocument(null);
            }
            // rename temporary annotations
            if (! debug) {
                renameAnnotations(outputAS, "tmpAnatomicalTerm", anatomyType);
                renameAnnotations(outputAS, "suffDisease", diseaseType);
                renameAnnotations(outputAS, "poDisease", diseaseType);
                renameAnnotations(outputAS, "preDisease", diseaseType);
                renameAnnotations(outputAS, "poProcedure", procedureType);
                renameAnnotations(outputAS, "poSymptom", symptomType);
                renameAnnotations(outputAS, "poTest", testType);
            }
        } else {
            gracefulExit("No sentences to process!");
        }

        // want list of disease key words plus symptoms such as oedema? or just diseases

        fireProcessFinished();
    } // end execute()


    /**
     * Rename annotation
     * @param outputAS          output annotation set
     * @param oldType           old annotation name
     * @param newType           new annotation name
     */
    private void renameAnnotations(AnnotationSet outputAS, String oldType, String newType) {
        AnnotationSet tmpAnatomyAS = outputAS.get(oldType);
        for (Annotation tmpAnn : tmpAnatomyAS) {
            Long startOffset = tmpAnn.getStartNode().getOffset();
            Long endOffset = tmpAnn.getEndNode().getOffset();
            AnnotationSet existingAS = outputAS.getCovering(newType, startOffset, endOffset);
            // If we've already got an annotation of the same name in the same place, don't add a new one
            // just delete the old one
            if (existingAS.isEmpty()) {
                FeatureMap tmpFm = tmpAnn.getFeatures();
                FeatureMap fm = Factory.newFeatureMap();
                fm.putAll(tmpFm);
                try {
                    outputAS.add(startOffset, endOffset, newType, fm);
                    outputAS.remove(tmpAnn);
                } catch (InvalidOffsetException ie) {
                    // shouldn't happen
                }
            } else {
                outputAS.remove(tmpAnn);
            }
        }
    }

    /**
     *
     * @param m
     * @param inputAS
     * @param outputAS
     * @param max
     * @throws ExecutionException
     */
    private void doMatch(Pattern p, String content, AnnotationSet inputAS, AnnotationSet outputAS, String outputASType, Long offsetAdjust, int max) throws ExecutionException {
        boolean useNounChunk = true;
        
        if (p == null) { return ; }
        if (outputASType.startsWith("tmp")) {
            useNounChunk = false;
        }
        Matcher m = p.matcher(content);
        int i = 0;
        while (m.find()) {
            i++;
            // Progress bar
            fireProgressChanged(i / max);
            if (isInterrupted()) {
                throw new ExecutionException("Execution of Anatomy, Disease Procedure Finder was interrupted.");
            }
            String term = m.group(0);
            Long startOffset = new Long(m.start(0));
            Long endOffset = new Long(m.end(0));
            addLookup(inputAS, outputAS, term, outputASType, startOffset + offsetAdjust, endOffset + offsetAdjust, useNounChunk);
        }
    }

    /**
     *
     * @param inputAS           input annotation set
     * @param outputAS          output annotation set
     * @param term              String matched
     * @param startOffset       match start offset
     * @param endOffset         match end offset
     */
    private void addLookup(AnnotationSet inputAS, AnnotationSet outputAS, String term, String outputASType, Long startOffset, Long endOffset, boolean useNounChunk) {
        if (useNounChunk && nounChunkType != null && !nounChunkType.isEmpty()) {
            AnnotationSet nounChunkAS = inputAS.getCovering(nounChunkType, startOffset, endOffset);
            if (!nounChunkAS.isEmpty()) {
                startOffset = nounChunkAS.firstNode().getOffset();
                endOffset = nounChunkAS.lastNode().getOffset();
            }
        }
        try {
            AnnotationSet diseaseAS = inputAS.get(outputASType, startOffset, endOffset);
            if (diseaseAS.isEmpty()) {
                FeatureMap fm = Factory.newFeatureMap();
                fm.put("match", term);
                outputAS.add(startOffset, endOffset, outputASType, fm);
            } else {
                Annotation disease = diseaseAS.iterator().next();
                FeatureMap fm = disease.getFeatures();
                String meta = (String) fm.get("match");
                if (meta != null) {
                    meta = meta + " " + term;
                }
                fm.put("match", meta);
            }

        } catch (InvalidOffsetException ie) {
            // shouldn't happen
            gate.util.Err.println(ie);
        }
    }


    /* Set gracefulExit flag and clean up */
    private void gracefulExit(String msg) {
        gate.util.Err.println(msg);
        cleanup();
        fireProcessFinished();
    }

    @Override
    public void cleanup() {
        Factory.deleteResource(japeTransducer);
    }

    @Override
    public synchronized void interrupt() {
        super.interrupt();
        japeTransducer.interrupt();
    }

    @Override
    public void progressChanged(int i) {
        fireProgressChanged(i);
    }

    @Override
    public void processFinished() {
        fireProcessFinished();
    }

    /* Setters and Getters
     * =======================
     */
    @Optional
    @RunTime
    @CreoleParameter(comment = "Input Annotation Set Name")
    public void setInputASName(String inputASName) {
        this.inputASName = inputASName;
    }

    public String getInputASName() {
        return inputASName;
    }

    @Optional
    @RunTime
    @CreoleParameter(comment = "Output Annotation Set Name")
    public void setOutputASName(String outputASName) {
        this.outputASName = outputASName;
    }

    public String getOutputASName() {
        return outputASName;
    }

    @Optional
    @RunTime
    @CreoleParameter(defaultValue = "DiseaseOrSyndrome", comment = "Output Annotation name for disease mentions")
    public void setDiseaseType(String diseaseType) {
        this.diseaseType = diseaseType;
    }

    public String getDiseaseType() {
        return diseaseType;
    }

    @Optional
    @RunTime
    @CreoleParameter(defaultValue = "AnatomicalSite", comment = "Output Annotation name for anatomy mentions")
    public void setAnatomyType(String anatomyType) {
        this.anatomyType = anatomyType;
    }

    public String getAnatomyType() {
        return anatomyType;
    }


    @Optional
    @RunTime
    @CreoleParameter(defaultValue = "Procedure", comment = "Output Annotation name for procedure mentions")
    public void setProcedureType(String procedureType) {
        this.procedureType = procedureType;
    }

    public String getProcedureType() {
        return procedureType;
    }

    @Optional
    @RunTime
    @CreoleParameter(defaultValue = "Symptom", comment = "Output Annotation name for symptom mentions")
    public void setSymptomType(String symptomType) {
        this.symptomType = symptomType;
    }

    public String getSymptomType() {
        return symptomType;
    }
    
    
    @Optional
    @RunTime
    @CreoleParameter(defaultValue = "Test", comment = "Output Annotation name for test mentions")
    public void setTestType(String testType) {
        this.testType = testType;
    }

    public String getTestType() {
        return testType;
    }


    @CreoleParameter(defaultValue = "2", comment = "Minimum length of prefix string before a suffix")
    public void setMinPrefixLength(Integer minPrefixLength) {
        this.minPrefixLength = minPrefixLength;
    }

    public Integer getMinPrefixLength() {
        return minPrefixLength;
    }

    public URL getConfigFileURL() {
        return configFileURL;
    }

    @CreoleParameter(defaultValue = "resources/config.txt",
    comment = "Location of configuration file")
    public void setConfigFileURL(URL configFileURL) {
        this.configFileURL = configFileURL;
    }

    @Optional
    @RunTime
    @CreoleParameter(defaultValue = ANNIEConstants.SENTENCE_ANNOTATION_TYPE,
    comment = "Sentence annotation name")
    public void setSentenceType(String sentenceName) {
        this.sentenceType = sentenceName;
    }

    public String getSentenceType() {
        return sentenceType;
    }

    @Optional
    @RunTime
    @CreoleParameter(defaultValue = "NounChunk",
    comment = "Name of Noun Phrase chunk annotation")
    public void setNounChunkType(String nounChunkType) {
        this.nounChunkType = nounChunkType;
    }

    public String getNounChunkType() {
        return nounChunkType;
    }

    @CreoleParameter(defaultValue = "resources/jape/main.jape",
    comment = "Location of main JAPE file")
    public void setJapeURL(URL japeURL) {
        this.japeURL = japeURL;
    }

    public URL getJapeURL() {
        return japeURL;
    }


    @RunTime
    @CreoleParameter(defaultValue = "false",
    comment = "Output debug annotations")
    public void setDebug(Boolean debug) {
        this.debug = debug;
    }

    public Boolean getDebug() {
        return debug;
    }
}

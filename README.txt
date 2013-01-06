This GATE plugin uses neoclassical-type morphemes and key words to identify potential anatomical, disease and clinical procedure mentions in the text.

For best results, add this plugin at the end of a pipeline that contains a Tokenizer, Sentence Splitter, POS Tagger and Noun Phrase Chunker.


Parameters
=========

Init-time
----------
configFileURL:	Location of configuration file that lists the lookup files
japeURL:	Location of JAPE grammar file.
minPrefixLength:	Minimum number of characters prefixing a suffix to trigger a match.


Run-time
---------
anatomyType:			Output annotation type for anatomy mentions
diseaseType:			Output annotation type for disease mentions.
inputASName:			Input annotation set name
nounChunkType:			Annotation type for noun phrase chunks. Defaults to NounChunk (change to NP if using MuNPex).
outputASName:			Output annotation set name
procedureType:			Output annotation type for procedure mentions. Currently quite limited.
sentenceType:			Annotation type for Sentence annotations. Defaults to Sentence.
symptomType:			Output annotation type for symptom mentions. Currently quite limited.
testType:			Output annotation type for lab test mentions. Currently very limited.



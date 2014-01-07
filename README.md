Tool for indexing data for LireSolr project
================================
Creates an index from images for SOLR. This data can be used for fast image retrieving. It uses *Color Layout* and *SURF* methods.
For more information check [LireSolr project](https://github.com/moravianlibrary/liresolr)

Compilation
--------------
Just type

```shell
ant package
```

Configutation
----------------
Check **config.properties** file.

```properties
# Url to the solr core, where indexer will import data.
solrCoreUrl = http://localhost:8983/solr/[core]
# Path to the solr core data folder, where indexer copy clusters-surf.dat.
solrCoreData = /[pathToCore]/data
# Number of the documents, which will be used to create vocabulary (clusters-surf.dat).
numDocsForVocabulary = 800
# This is equivalent to a number of the visual words, which will be used to search images.
numClusters = 1000
# Number of threads, which will be used to index images.
numberOfThreads = 2
```

Usage
--------
###Index images:
```shell
java -jar indexer.jar index <file>
```

####Parameters:
-   **file** .. text file containing paths to the images (one line = one path)

####Example:
*images*
```text
data/image1.jpg
data/image2.jpg
data/image3.jpg
```

```shell
java -jar indexer.jar index images
```

###Import images to SOLR:
```shell
java -jar indexer.jar import
```

This method has no parameters. Parameters are set in the **config.properties** file.

###Create visual words
Creates data for visual words technique. This step is automatically execute after index step. You can execute this step again if you want to create visual words with other parameters specific in the **config.properties** file.

```shell
java -jar indexer.jar visualwords
```

*Erich Duda, 2013-12-02*

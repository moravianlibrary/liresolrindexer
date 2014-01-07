package net.semanticmetadata.lire.solr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

import javax.swing.ProgressMonitor;

import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.ColorLayout;
import net.semanticmetadata.lire.imageanalysis.bovw.LocalFeatureHistogramBuilder;
import net.semanticmetadata.lire.imageanalysis.bovw.SurfFeatureHistogramBuilder;
import net.semanticmetadata.lire.impl.ChainedDocumentBuilder;
import net.semanticmetadata.lire.impl.SurfDocumentBuilder;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;


public class Main {

	public static final void main(String[] args) {
		
		if (args.length == 2 && "index".equals(args[0])) {
			
			if ("index".equals(args[0])) {
				try {
					createIndex(args[1]);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					System.exit(1);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
			} else {
				printHelp();
			}
		} else if (args.length == 1) {
			if ("import".equals(args[0])) {
				try {
					importIndex();
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				} catch (SolrServerException e) {
					e.printStackTrace();
					System.exit(1);
				}
			} else if ("visualwords".equals(args[0])) {
				try {
					visualWords();
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
			} else {
				printHelp();
			}
		} else {
			printHelp();
		}
	}
	
	private static void createIndex(String imagesFile) throws FileNotFoundException, IOException {
		int numberOfThreads = Integer.parseInt(getProperties().getProperty("numberOfThreads"));
		ParallelIndexer indexer = new ParallelIndexer(numberOfThreads, "index", new File(imagesFile)) {
			public void addBuilders(ChainedDocumentBuilder builder) {
				builder.addBuilder(new SurfDocumentBuilder());
				builder.addBuilder(new GenericDocumentBuilder(ColorLayout.class, DocumentBuilder.FIELD_NAME_COLORLAYOUT, true));
			}
		};
		indexer.run();
		
		System.out.println("Indexing finished");
		System.out.println("Creating visual words...");
		
		IndexReader ir = DirectoryReader.open(FSDirectory.open(new File("index")));
		LocalFeatureHistogramBuilder.DELETE_LOCAL_FEATURES = false;
		int numDocsForVocabulary = Integer.parseInt(getProperties().getProperty("numDocsForVocabulary"));
		int numClusters = Integer.parseInt(getProperties().getProperty("numClusters"));
		SurfFeatureHistogramBuilder sh = new SurfFeatureHistogramBuilder(ir, numDocsForVocabulary, numClusters);
		sh.setProgressMonitor(new ProgressMonitor(null, "", "", 0, 100));
		sh.index();
		System.out.println("Creating visual words finished.");
		System.out.println("Now you can import data to solr by typing.");
		System.out.println("java -jar indexer.jar import");
	}
	
	private static void importIndex() throws IOException, SolrServerException {
		Properties prop = getProperties();
		String solrCoreData = prop.getProperty("solrCoreData");
		System.out.println("Copying clusters-surf.dat to " + solrCoreData);
		FileUtils.copyFile(new File("clusters-surf.dat"), new File(solrCoreData + "/clusters-surf.dat"));
		
		String url = prop.getProperty("solrCoreUrl");
		System.out.println("Load data to: " + url);
		SolrServer server = new HttpSolrServer(url);
		
		Collection<SolrInputDocument> buffer = new ArrayList<>(30);
		
		IndexReader reader = DirectoryReader.open(FSDirectory.open(new File("index")));
		for (int i = 0; i < reader.maxDoc(); ++i) {
			Document doc = reader.document(i);
			SolrInputDocument inputDoc = new SolrInputDocument();
			// ID
			inputDoc.addField("id", doc.getField(DocumentBuilder.FIELD_NAME_IDENTIFIER).stringValue());
			// ColorLayout
			BytesRef clHiBin = doc.getField(DocumentBuilder.FIELD_NAME_COLORLAYOUT).binaryValue();
			inputDoc.addField("cl_hi", ByteBuffer.wrap(clHiBin.bytes, clHiBin.offset, clHiBin.length));
			//inputDoc.addField("cl_hi", Base64.byteArrayToBase64(clHiBin.bytes, clHiBin.offset, clHiBin.length));
			inputDoc.addField("cl_ha", doc.getField(DocumentBuilder.FIELD_NAME_COLORLAYOUT + GenericDocumentBuilder.HASH_FIELD_SUFFIX).stringValue());
			// SURF
			IndexableField[] features = doc.getFields(DocumentBuilder.FIELD_NAME_SURF);
			for (IndexableField feature : features) {
				BytesRef featureBin = feature.binaryValue();
				inputDoc.addField("su_hi", ByteBuffer.wrap(featureBin.bytes, featureBin.offset, featureBin.length));
				//inputDoc.addField("su_hi", Base64.byteArrayToBase64(feature.binaryValue().bytes, feature.binaryValue().offset, feature.binaryValue().bytes.length));
			}
			inputDoc.addField("su_ha", doc.getField(DocumentBuilder.FIELD_NAME_SURF_VISUAL_WORDS).stringValue());
			
			buffer.add(inputDoc);
			
			if (buffer.size() >= 1) {
				// Flush buffer
				server.add(buffer);
				buffer.clear();
			}
		}
		
		if (buffer.size() > 0) {
			server.add(buffer);
			buffer.clear();
		}
		
		try {
			server.commit();
			server.shutdown();
		} catch (SolrServerException e) {
			e.printStackTrace();
		}
	}
	
	private static void visualWords() throws IOException {
		Properties prop = getProperties();
		IndexReader ir = DirectoryReader.open(FSDirectory.open(new File("index")));
		LocalFeatureHistogramBuilder.DELETE_LOCAL_FEATURES = false;
		int numDocsForVocabulary = Integer.parseInt(prop.getProperty("numDocsForVocabulary"));
		int numClusters = Integer.parseInt(prop.getProperty("numClusters"));
		SurfFeatureHistogramBuilder sh = new SurfFeatureHistogramBuilder(ir, numDocsForVocabulary, numClusters);
		sh.setProgressMonitor(new ProgressMonitor(null, "", "", 0, 100));
		sh.index();
	}
	
	private static Properties getProperties() {
		Properties prop = new Properties();
		
		try {
			prop.load(new FileInputStream("./config.properties"));
		} catch (IOException e) {
			System.out.println("Cannot read config.properties file.");
		}
		return prop;
	}
	
	private static void printHelp() {
		System.out.println("USAGE:");
		System.out.println("\t index file - File contains paths to the images, which will be indexed.");
		System.out.println("\t import - It sends data from index to solr server specific in the config.properties file.");
		System.out.println("\t visualwords - It creates data for visual words technique. This step is automatically execute after index step. You can execute this step again if you want to create visual words with other parameters specific in config.properties file.");
	}
}

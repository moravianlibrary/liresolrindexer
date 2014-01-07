package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.AbstractDocumentBuilder;
import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.imageanalysis.joint.JointHistogram;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.indexing.hashing.LocalitySensitiveHashing;
import net.semanticmetadata.lire.utils.ImageUtils;
import org.apache.lucene.document.*;


import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * This class allows to create a DocumentBuilder based on a class implementing LireFeature.
 * Date: 28.05.2008
 * Time: 14:32:15
 *
 * @author Mathias Lux, mathias@juggle.at
 * @author sangupta, sandy.pec@gmail.com (fixed null pointer exception)
 */
public class GenericDocumentBuilder extends AbstractDocumentBuilder {
    enum HashingMode {BitSampling, LSH}

    private boolean hashingEnabled = false;
    private Logger logger = Logger.getLogger(getClass().getName());
    public static final int MAX_IMAGE_DIMENSION = 1024;
    Class<? extends LireFeature> descriptorClass;
    String fieldName;
    final static Mode DEFAULT_MODE = Mode.Fast;
    Mode currentMode = DEFAULT_MODE;
    // private LireFeature lireFeature;
    protected HashingMode hashingMode = HashingMode.BitSampling;

    public static HashMap<Class, String> fieldForClass = new HashMap<Class, String>();
    public static HashMap<String, Class> classForField = new HashMap<String, Class>();

    public static final String HASH_FIELD_SUFFIX = "_hash";



    static {
        // Let's try to read the hash functions right here and we don't have to care about it right now.
        try {
            BitSampling.readHashFunctions();
//            LocalitySensitiveHashing.readHashFunctions();
        } catch (IOException e) {
            System.err.println("Could not read hashes from file when first creating a GenericDocumentBuilder instance.");
            e.printStackTrace();
        }

        // Setting up the class 2 field relation:
        fieldForClass.put(AutoColorCorrelogram.class, FIELD_NAME_AUTOCOLORCORRELOGRAM);
        fieldForClass.put(BinaryPatternsPyramid.class, FIELD_NAME_BINARY_PATTERNS_PYRAMID);
        fieldForClass.put(CEDD.class, FIELD_NAME_CEDD);
        fieldForClass.put(SimpleColorHistogram.class, FIELD_NAME_COLORHISTOGRAM);
        fieldForClass.put(ColorLayout.class, FIELD_NAME_COLORLAYOUT);
        fieldForClass.put(EdgeHistogram.class, FIELD_NAME_EDGEHISTOGRAM);
        fieldForClass.put(FCTH.class, FIELD_NAME_FCTH);
        fieldForClass.put(Gabor.class, FIELD_NAME_GABOR);
        fieldForClass.put(JCD.class, FIELD_NAME_JCD);
        fieldForClass.put(JointHistogram.class, FIELD_NAME_JOINT_HISTOGRAM);
        fieldForClass.put(JpegCoefficientHistogram.class, FIELD_NAME_JPEGCOEFFS);
        fieldForClass.put(LocalBinaryPatterns.class, FIELD_NAME_LOCAL_BINARY_PATTERNS);
        fieldForClass.put(LuminanceLayout.class, FIELD_NAME_LUMINANCE_LAYOUT);
        fieldForClass.put(OpponentHistogram.class, FIELD_NAME_OPPONENT_HISTOGRAM);
        fieldForClass.put(PHOG.class, FIELD_NAME_PHOG);
        fieldForClass.put(RotationInvariantLocalBinaryPatterns.class, FIELD_NAME_ROTATION_INVARIANT_LOCAL_BINARY_PATTERNS);
        fieldForClass.put(ScalableColor.class, FIELD_NAME_SCALABLECOLOR);
        fieldForClass.put(Tamura.class, FIELD_NAME_TAMURA);

        // Setting up the field 2 class relation:
        classForField.put(FIELD_NAME_AUTOCOLORCORRELOGRAM, AutoColorCorrelogram.class);
        classForField.put(FIELD_NAME_BINARY_PATTERNS_PYRAMID, BinaryPatternsPyramid.class);
        classForField.put(FIELD_NAME_CEDD, CEDD.class);
        classForField.put(FIELD_NAME_COLORHISTOGRAM, SimpleColorHistogram.class);
        classForField.put(FIELD_NAME_COLORLAYOUT, ColorLayout.class);
        classForField.put(FIELD_NAME_EDGEHISTOGRAM, EdgeHistogram.class);
        classForField.put(FIELD_NAME_FCTH, FCTH.class);
        classForField.put(FIELD_NAME_GABOR, Gabor.class);
        classForField.put(FIELD_NAME_JCD, JCD.class);
        classForField.put(FIELD_NAME_JOINT_HISTOGRAM, JointHistogram.class);
        classForField.put(FIELD_NAME_JPEGCOEFFS, JpegCoefficientHistogram.class);
        classForField.put(FIELD_NAME_LOCAL_BINARY_PATTERNS, LocalBinaryPatterns.class);
        classForField.put(FIELD_NAME_LUMINANCE_LAYOUT, LuminanceLayout.class);
        classForField.put(FIELD_NAME_OPPONENT_HISTOGRAM, OpponentHistogram.class);
        classForField.put(FIELD_NAME_PHOG, PHOG.class);
        classForField.put(FIELD_NAME_ROTATION_INVARIANT_LOCAL_BINARY_PATTERNS, RotationInvariantLocalBinaryPatterns.class);
        classForField.put(FIELD_NAME_SCALABLECOLOR, ScalableColor.class);
        classForField.put(FIELD_NAME_TAMURA, Tamura.class);
    }

    // Decide between byte array version (fast) or string version (slow)
    public enum Mode {
        Fast, Slow
    }

    /**
     * Creating a new DocumentBuilder based on a class based on the interface {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     *
     * @param descriptorClass has to implement {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     * @param fieldName       the field hashFunctionsFileName in the index.
     */
    public GenericDocumentBuilder(Class<? extends LireFeature> descriptorClass, String fieldName) {
        this.descriptorClass = descriptorClass;
        this.fieldName = fieldName;
    }

    /**
     * Creating a new DocumentBuilder based on a class based on the interface
     * {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     *
     * @param descriptorClass has to implement {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     */
    public GenericDocumentBuilder(Class<? extends LireFeature> descriptorClass) {
        this.descriptorClass = descriptorClass;
        this.fieldName = fieldForClass.get(descriptorClass);
        if (fieldName == null) {
            try {
                fieldName = descriptorClass.newInstance().getFieldName();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Creating a new DocumentBuilder based on a class based on the interface
     * {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     *
     * @param descriptorClass has to implement {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     * @param hashing         set to true is you want to create an additional field for hashes based on BitSampling.
     * @param mode the hashing mode you want to use. default is bit sampling, but there is also a vector based LSH version.
     */
    public GenericDocumentBuilder(Class<? extends LireFeature> descriptorClass, boolean hashing, HashingMode mode) {
        this.descriptorClass = descriptorClass;
        this.fieldName = fieldForClass.get(descriptorClass);
        this.hashingMode = mode;
        if (fieldName == null) {
            try {
                fieldName = descriptorClass.newInstance().getFieldName();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        hashingEnabled = hashing;
    }

    public GenericDocumentBuilder(Class<? extends LireFeature> descriptorClass, boolean hashing) {
        this.descriptorClass = descriptorClass;
        this.fieldName = fieldForClass.get(descriptorClass);
        if (fieldName == null) {
            try {
                fieldName = descriptorClass.newInstance().getFieldName();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        hashingEnabled = hashing;
    }

    /**
     * Creating a new DocumentBuilder based on a class based on the interface {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     *
     * @param descriptorClass has to implement {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     * @param fieldName       The hashFunctionsFileName of the field, where the feature vector is stored.
     * @param hashing         set to true is you want to create an additional field for hashes based on BitSampling.
     */
    public GenericDocumentBuilder(Class<? extends LireFeature> descriptorClass, String fieldName, boolean hashing) {
        this.descriptorClass = descriptorClass;
        this.fieldName = fieldName;
        hashingEnabled = hashing;
    }

    /**
     * Creating a new DocumentBuilder based on a class based on the interface {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     *
     * @param descriptorClass has to implement {@link net.semanticmetadata.lire.imageanalysis.LireFeature}
     * @param fieldName       the field hashFunctionsFileName in the index.
     * @param mode            the mode the GenericDocumentBuilder should work in, byte[] (== Mode.Fast) or string (==Mode.Slow) storage in Lucene.
     */
    public GenericDocumentBuilder(Class<? extends LireFeature> descriptorClass, String fieldName, Mode mode) {
        this.descriptorClass = descriptorClass;
        this.fieldName = fieldName;
        this.currentMode = mode;
    }

    public Field[] createDescriptorFields(BufferedImage image) {
        Field[] result;
        if (hashingEnabled) result = new Field[2];
        else result = new Field[1];
        String featureString = "";
        assert (image != null);
        BufferedImage bimg = image;
        // Scaling image is especially with the correlogram features very important!
        // All images are scaled to guarantee a certain upper limit for indexing.
        if (Math.max(image.getHeight(), image.getWidth()) > MAX_IMAGE_DIMENSION) {
            bimg = ImageUtils.scaleImage(image, MAX_IMAGE_DIMENSION);
        }
        Document doc = null;
        try {
            logger.finer("Starting extraction from image [" + descriptorClass.getName() + "].");
            LireFeature lireFeature = null;

            lireFeature = descriptorClass.newInstance();

            lireFeature.extract(bimg);
//            featureString = vd.getStringRepresentation();
            logger.fine("Extraction finished [" + descriptorClass.getName() + "].");

            // TODO: Stored field is compressed and upon search decompression takes a lot of time (> 50% with a small index with 50k images). Find something else ...
            result[0] = new StoredField(fieldName, lireFeature.getByteArrayRepresentation());

            // if BitSampling is an issue we add a field with the given hashFunctionsFileName and the suffix "hash":
            if (hashingEnabled) {
                // TODO: check eventually if there is a more compressed string version of the integers. i.e. the hex string
                if (lireFeature.getDoubleHistogram().length <= 3100) {
                    int[] hashes;
                    if (hashingMode == HashingMode.BitSampling) {
                        hashes = BitSampling.generateHashes(lireFeature.getDoubleHistogram());
                    } else {
                        hashes = LocalitySensitiveHashing.generateHashes(lireFeature.getDoubleHistogram());
                    }
                    result[1] = new TextField(fieldName + HASH_FIELD_SUFFIX, arrayToString(hashes), Field.Store.YES);
                } else
                    System.err.println("Could not create hashes, feature vector too long: " + lireFeature.getDoubleHistogram().length + " (" + lireFeature.getClass().getName() + ")");
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Creates a fully fledged Document to be added to a Lucene index.
     *
     * @param image      the image to index. Cannot be NULL.
     * @param identifier an id for the image, for instance the filename or an URL. Can be NULL.
     * @return
     */
    public Document createDocument(BufferedImage image, String identifier) {
        assert (image != null);

        // sangupta: create a new document else code below
        // will throw a NPE
        Document doc = new Document();

        if (identifier != null) {
            doc.add(new StringField(DocumentBuilder.FIELD_NAME_IDENTIFIER, identifier, Field.Store.YES));
        }

        Field[] fields = createDescriptorFields(image);
        for (int i = 0; i < fields.length; i++) {
            doc.add(fields[i]);
        }

        return doc;
    }
    
    public static String arrayToString(int[] array) {
        StringBuilder sb = new StringBuilder(array.length * 8);
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Integer.toHexString(array[i]));
        }
        return sb.toString();
    }
}

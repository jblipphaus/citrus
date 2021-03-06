/*
 * Copyright 2006-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.doc;

import java.io.*;
import java.util.List;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.consol.citrus.TestCase;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.util.FileUtils;

/**
 * Class to automatically generate a visual representation of a {@link TestCase} in SVG.
 * 
 * @author Christoph Deppisch
 * @since 2007
 */
public final class SvgTestDocGenerator {
    private static final String DEFAULT_XSLT_SOURCE = "generate-svg-doc.xslt";
    private static final String DEFAULT_TEST_DIRECTORY = "tests";

    /**
     * Logger
     */
    private static Logger log = LoggerFactory.getLogger(SvgTestDocGenerator.class);

    /**
     * Prevent instantiation.
     */
    private SvgTestDocGenerator() {
    }
    
    public static void main(String[] args) {
        try {
            String testDirectory = DEFAULT_TEST_DIRECTORY;

            if (args.length > 1) {
                testDirectory = args[1];
            }

            List<File> testFiles = FileUtils.getTestFiles(testDirectory);

            String xslSource;
            if (args.length > 0) {
                xslSource = args[0];
            } else {
                xslSource = DEFAULT_XSLT_SOURCE;
            }

            Source xsl = new StreamSource(SvgTestDocGenerator.class.getResourceAsStream(xslSource));
            log.info("XSLT stylesheet was set: " + xsl.getSystemId());

            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer t = factory.newTransformer(xsl);

            t.setOutputProperty(OutputKeys.METHOD, "xml");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            log.info("XSL transformer was created");

            for (File testFile : testFiles) {
                log.info("Working on test " + testFile.getName());

                StringWriter stringWriter = new StringWriter();
                StreamSource xml = new StreamSource(testFile);
                StreamResult res = new StreamResult(stringWriter);
                
                FileWriter fileWriter = null;
                
                try {
                    t.transform(xml, res);
                    stringWriter.flush();

                    String fileContent = stringWriter.toString();
                    stringWriter.close();

                    if (fileContent!= null && fileContent.indexOf("svg")!=-1) {
                        log.info("Created file " + testFile.getName().substring(0, testFile.getName().lastIndexOf('.')) + ".svg");
                        fileWriter = new FileWriter(testFile.getName().substring(0, testFile.getName().lastIndexOf('.')) + ".svg");
                        fileWriter.write(stringWriter.toString());
                        fileWriter.flush();
                    } else {
                        log.warn("Could not create file " + testFile.getName().substring(0, testFile.getName().lastIndexOf('.')) + ".svg");
                    }
                } catch(TransformerException e) {
                    throw new CitrusRuntimeException("XSLT tranformation failed", e);
                } catch (IOException e) {
                    throw new CitrusRuntimeException("Failed to generate test documentation, IO error", e);
                } finally {
                    if(fileWriter != null) {
                        try {
                            fileWriter.close();
                        } catch (IOException e) {
                            log.error("Failed to close test documentation file", e);
                        }
                    }
                }
            }
        } catch (TransformerConfigurationException e) {
            throw new CitrusRuntimeException("Error during doc generation", e);
        }
    }
}

package com.buschmais.jqassistant.plugin.yaml2.impl.scanner;

import java.io.IOException;
import java.io.InputStream;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.ScannerContext;
import com.buschmais.jqassistant.core.scanner.api.ScannerPlugin;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import com.buschmais.jqassistant.plugin.yaml2.api.model.YMLDescriptor;
import com.buschmais.jqassistant.plugin.yaml2.api.model.YMLDocumentDescriptor;
import com.buschmais.jqassistant.plugin.yaml2.api.model.YMLFileDescriptor;

import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.events.Event;

import static java.lang.String.format;

@ScannerPlugin.Requires(FileDescriptor.class)
public class YMLFileScannerPlugin extends AbstractScannerPlugin<FileResource, YMLFileDescriptor> {

    /**
     * Supported file extensions for YAML file resources.
     */
    public final static String YAML_FILE_EXTENSION = ".yaml";
    public final static String YML_FILE_EXTENSION = ".yml";

    private ParsingContext context = new ParsingContext();

    @Override
    public boolean accepts(FileResource file, String path, Scope scope) {
        String lowercasePath = path.toLowerCase();
        return lowercasePath.endsWith(YAML_FILE_EXTENSION) || lowercasePath.endsWith(YML_FILE_EXTENSION);
    }

    @Override
    public YMLFileDescriptor scan(FileResource item, String path, Scope scope, Scanner scanner) throws IOException {
        ScannerContext context = scanner.getContext();
        LoadSettings settings = LoadSettings.builder().build();
        FileDescriptor fileDescriptor = context.getCurrentDescriptor();

        // todo implement handleFileEnd
        // todo take it from the parsing context
        YMLFileDescriptor yamlFileDescriptor = handleFileStart(fileDescriptor);


        try (InputStream in = item.createStream()) {
            Parse parser = new Parse(settings);
            Iterable<Event> events = parser.parseInputStream(in);
            processEvents(events);
        } catch (RuntimeException re) {
            // todo Improve the errorhandling
            throw re;
        }

        return yamlFileDescriptor;
    }

    private void processEvents(Iterable<Event> events) {
        // verbrauchend
        // events.forEach(e -> { System.out.print(e.getClass() + "##");System.out.println("##" + e + "##"); });

        for (Event event : events) {
            switch (event.getEventId()) {
                case StreamStart:
                    handleStreamStart(event);
                    break;
                case DocumentStart:
                    handleDocumentStart(event);
                    break;
            }
        }


    }

    private YMLFileDescriptor handleFileStart(FileDescriptor fileDescriptor) {
        YMLFileDescriptor yamlFileDescriptor = getScannerContext().getStore().addDescriptorType(fileDescriptor, YMLFileDescriptor.class);
        ContextType inFile = ContextType.ofInFile(yamlFileDescriptor);
        context.enter(inFile);
        return yamlFileDescriptor;
    }


    private void handleDocumentStart(Event event) {
        if (context.isNotInStream()) {
            throwIllegalStateException(ContextType.Type.IN_SEQUENCE, context.peek().getType());
        }

        YMLDocumentDescriptor descriptor = createDescriptor(YMLDocumentDescriptor.class);
        ContextType inDocument = ContextType.ofInDocument(descriptor);

        context.enter(inDocument);

        ContextType fileContext = context.getAncestor(ContextType.Ancestor.SECOND);
        YMLFileDescriptor dddd = (YMLFileDescriptor) fileContext.getDescriptor();
        dddd.getDocuments().add((YMLDocumentDescriptor) inDocument.getDescriptor());
    }

    private <D extends YMLDescriptor> D createDescriptor(Class<D> descriptorType) {
        return getScannerContext().getStore().create(descriptorType);
    }

    private void handleStreamStart(Event event) {
        ContextType inStream = ContextType.ofStream();
        context.enter(inStream);
    }

    private void throwIllegalStateException(ContextType.Type expected,
                                            ContextType.Type actual) {
        // todo Which type of exception to throw in case of a wrong state
        String message = format("Wrong internal state during parsing a YAML " +
                                "document. Expected content: %d, actual " +
                                "context: %d", expected, actual);

        throw new IllegalStateException(message);
    }
}
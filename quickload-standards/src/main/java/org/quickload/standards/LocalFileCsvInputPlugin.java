package org.quickload.standards;

import com.google.common.base.Function;
import com.google.inject.Inject;
import org.quickload.buffer.Buffer;
import org.quickload.config.Config;
import org.quickload.config.ConfigSource;
import org.quickload.config.DynamicModel;
import org.quickload.exec.BufferManager;
import org.quickload.plugin.PluginManager;
import org.quickload.record.PageAllocator;
import org.quickload.record.Schema;
import org.quickload.spi.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

public class LocalFileCsvInputPlugin
        extends FileInputPlugin<LocalFileCsvInputPlugin.Task>
{
    @Inject
    public LocalFileCsvInputPlugin(PluginManager pluginManager) {
        super(pluginManager);
    }

    // TODO initialize page allocator object
    // TODO consider when the page allocator object is released?

    public interface Task
            extends FileInputTask, DynamicModel<Task>
    {
        @Config("in:paths") // TODO temporarily added 'in:'
        public List<String> getPaths();

        @Config("in:schema") // TODO temporarily added 'in:'
        public Schema getSchema();

        @Config("ConfigExpression")
        public String getConfigExpression(); // TODO make it more intuitive

        public ParserTask getParserTask();
    }

    @Override
    public Task getTask(ConfigSource config)
    {
        Task task = config.load(Task.class);
        task.set("ProcessorCount", task.getPaths().size());

        MyParserTask parserTask = config.load(MyParserTask.class);
        parserTask.set("Schema", task.getSchema());

        task.set("ParserTask", parserTask);

        return task.validate();
    }

    // TODO is the declaration needed? ParserTask should implement DynamicModel??
    public interface MyParserTask
            extends ParserTask, DynamicModel<MyParserTask>
    {
    }

    // TODO port startFileInputProcessor
    @Override
    public InputProcessor startFileInputProcessor(final Task task,
            final int processorIndex, final BufferOperator op)
    {
        return ThreadInputProcessor.start(op, new Function<BufferOperator, ReportBuilder>() {
            public ReportBuilder apply(BufferOperator op) {
                return readFile(task, processorIndex, op);
            }
        });
    }

    public static ReportBuilder readFile(Task task, int processorIndex, BufferOperator op)
    {
        // TODO ad-hoc
        String path = task.getPaths().get(processorIndex);

        try {
            File file = new File(path);
            byte[] bytes = new byte[(int) file.length()]; // TODO ad-hoc

            int len, offset = 0;
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                while ((len = in.read(bytes, offset, bytes.length - offset)) > 0) {
                    offset += len;
                }
                Buffer buffer = new Buffer(bytes);
                op.addBuffer(buffer);
            }
        } catch (Exception e) {
            e.printStackTrace(); // TODO
        }

        return DynamicReport.builder(); // TODO
    }
}

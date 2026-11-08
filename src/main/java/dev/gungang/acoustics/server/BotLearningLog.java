package dev.gungang.acoustics.server;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Bounded asynchronous JSONL sink; a full queue drops rows instead of blocking a server tick. */
final class BotLearningLog {
    private final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(1024);
    private volatile boolean closing;
    private volatile boolean finished;
    volatile String failure="";
    volatile long written;
    long dropped;
    final Path path;
    private final Thread worker;
    BotLearningLog(Path directory)throws java.io.IOException {
        Files.createDirectories(directory);
        path=directory.resolve("session-"+java.util.UUID.randomUUID()+".jsonl");
        var writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        worker=new Thread(()->{
            try(writer) {
                while(!closing||!queue.isEmpty()) {
                    String row=queue.poll(250,TimeUnit.MILLISECONDS);
                    if(row!=null){writer.write(row);writer.newLine();written++;}
                    if(row==null||written%100==0)writer.flush();
                }
            }catch(Exception error){failure=error.toString();closing=true;queue.clear();}
            finally{finished=true;}
        },"gungang-learning-log");
        worker.setDaemon(true);worker.start();
    }
    void write(String json){if(closing||!failure.isEmpty()||written+queue.size()>=100000||!queue.offer(json))dropped++;}
    void close(){closing=true;}
    void finishOnShutdown(){
        close();
        try{worker.join(1500);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
    boolean finished(){return finished;}
}

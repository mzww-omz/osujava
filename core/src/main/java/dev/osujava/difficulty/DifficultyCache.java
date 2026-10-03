package dev.osujava.difficulty;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Worker-only immutable sidecars. A bad/future record is neither trusted nor overwritten. */
final class DifficultyCache {
    private static final int MAX_BYTES=64*1024;
    private final Path directory;
    DifficultyCache(Path directory) { this.directory=directory; }
    record Read(DifficultyResult result, boolean writable, String warning) { }
    Path path(DifficultyKey key) { return directory.resolve(key.filename()); }
    Read read(DifficultyKey key) {
        if(directory==null) return new Read(null,false,"");
        Path path=path(key);
        try {
            byte[] bytes;
            try(var in=Files.newInputStream(path)) { bytes=in.readNBytes(MAX_BYTES+1); }
            if(bytes.length>MAX_BYTES) throw new IOException("Oversized cache");
            var p=new Properties();p.load(new ByteArrayInputStream(bytes));
            if(!"1".equals(p.getProperty("schema")) || !key.content().sha256().equals(p.getProperty("sha256"))
                    || !Integer.toString(key.content().mode()).equals(p.getProperty("mode"))
                    || !String.join(",",key.mods()).equals(p.getProperty("mods")) || !key.algorithm().equals(p.getProperty("algorithm"))
                    || !key.preprocessing().equals(p.getProperty("preprocessing"))) throw new IOException("Unknown/mismatched cache schema or key");
            var status=DifficultyResult.Status.valueOf(p.getProperty("status"));
            if(status==DifficultyResult.Status.PENDING) throw new IOException("Incomplete cache");
            var result=new DifficultyResult(status,number(p,"stars"),number(p,"aim"),number(p,"speed"),p.getProperty("reason"));
            return new Read(result,false,"");
        } catch(NoSuchFileException missing) { return new Read(null,true,""); }
        catch(IOException | IllegalArgumentException | NullPointerException corrupt) {
            return new Read(null,false,"A difficulty cache record could not be read; retained unchanged");
        }
    }
    private Double number(Properties p,String field) { return p.containsKey(field) ? Double.valueOf(p.getProperty(field)) : null; }
    String save(DifficultyKey key,DifficultyResult result) {
        if(directory==null) return "";
        if(result.status()==DifficultyResult.Status.PENDING) throw new IllegalArgumentException("Cannot cache pending result");
        Path temporary=null;
        try {
            Files.createDirectories(directory);
            temporary=Files.createTempFile(directory,".difficulty-",".tmp");
            var p=new Properties();p.setProperty("schema","1");p.setProperty("sha256",key.content().sha256());p.setProperty("mode",Integer.toString(key.content().mode()));
            p.setProperty("mods",String.join(",",key.mods()));p.setProperty("algorithm",key.algorithm());p.setProperty("preprocessing",key.preprocessing());
            p.setProperty("status",result.status().name());p.setProperty("reason",result.reason());
            if(result.stars()!=null) { p.setProperty("stars",result.stars().toString());p.setProperty("aim",result.aim().toString());p.setProperty("speed",result.speed().toString()); }
            try(var out=Files.newOutputStream(temporary)) { p.store(out,"osu!java local difficulty cache"); }
            // Atomic publication of a complete file without replacing a concurrently created/future record.
            // Both paths are on the same filesystem. Unsupported hard links mean memory-only caching.
            Files.createLink(path(key),temporary);
            return "";
        } catch(FileAlreadyExistsException concurrent) { return ""; }
        catch(IOException | UnsupportedOperationException failure) { return "Difficulty cache could not be saved; calculated values remain available for this screen"; }
        finally { if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) { } }
    }
}

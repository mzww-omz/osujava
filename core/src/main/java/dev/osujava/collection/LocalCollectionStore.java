package dev.osujava.collection;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.score.DifficultyIdentity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

/** Local collection definitions. A mutation publishes only after the whole file is saved. */
public final class LocalCollectionStore {
    public enum Status { READY, UNAVAILABLE }
    public record Member(BeatmapContentKey content, DifficultyIdentity location) {
        public Member { Objects.requireNonNull(content); Objects.requireNonNull(location); }
    }
    public record Collection(UUID id, String name, List<Member> members) {
        public Collection {
            Objects.requireNonNull(id); name=validName(name); members=List.copyOf(members);
            if(members.stream().map(Member::content).distinct().count()!=members.size())
                throw new IllegalArgumentException("Duplicate collection member");
        }
    }
    private static final int MAX_COLLECTIONS=10_000, MAX_MEMBERS=100_000;
    private static final long MAX_BYTES=32*1024*1024;
    private final Path file;
    private List<Collection> snapshot=List.of();
    private byte[] diskBytes;
    private boolean readable=true;
    private long revision;
    private Status status=Status.READY;
    private String error="";
    public LocalCollectionStore() { file=null; }
    public LocalCollectionStore(Path file) { this.file=Objects.requireNonNull(file); load(); }
    public List<Collection> all() { return snapshot; }
    public long revision() { return revision; }
    public Status status() { return status; }
    public String error() { return error; }
    public Collection find(UUID id) { return snapshot.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null); }
    public Collection create(String name) {
        try {
            var next=new Collection(UUID.randomUUID(),name,List.of()); var values=new ArrayList<>(snapshot); values.add(next);
            return save(values) ? next : null;
        } catch(IllegalArgumentException e) { error=e.getMessage(); return null; }
    }
    public boolean rename(UUID id,String name) {
        var c=find(id); if(c==null) return false;
        try { return replace(new Collection(id,name,c.members())); }
        catch(IllegalArgumentException e) { error=e.getMessage(); return false; }
    }
    public boolean delete(UUID id) {
        if(find(id)==null) return false;
        return save(snapshot.stream().filter(c -> !c.id().equals(id)).toList());
    }
    public boolean add(UUID id,List<Member> members) {
        var c=find(id); if(c==null) return false;
        var unique=new LinkedHashMap<BeatmapContentKey,Member>();
        for(var m:c.members()) unique.put(m.content(),m);
        for(var m:members) unique.putIfAbsent(m.content(),m);
        return replace(new Collection(id,c.name(),List.copyOf(unique.values())));
    }
    public boolean remove(UUID id,Set<BeatmapContentKey> contents) {
        var c=find(id); if(c==null) return false;
        return replace(new Collection(id,c.name(),c.members().stream().filter(m -> !contents.contains(m.content())).toList()));
    }
    private boolean replace(Collection c) {
        if(c.equals(find(c.id()))) { error=""; return true; }
        return save(snapshot.stream().map(old -> old.id().equals(c.id()) ? c : old).toList());
    }
    private boolean save(List<Collection> values) {
        Path temporary=null;
        try {
            if(!readable) throw new IOException("Preserving unreadable collections");
            if(values.size()>MAX_COLLECTIONS || values.stream().mapToLong(c -> c.members().size()).sum()>MAX_MEMBERS)
                throw new IOException("Collection limit reached");
            var next=values.stream().sorted(Comparator.comparing(Collection::name).thenComparing(c -> c.id().toString())).toList();
            if(file!=null) {
                byte[] current=Files.exists(file) ? readBytes() : null;
                if(!Arrays.equals(diskBytes,current)) throw new IOException("Collections changed on disk");
                var p=new Properties(); p.setProperty("schemaVersion","1"); p.setProperty("collection.count",Integer.toString(next.size()));
                for(int i=0;i<next.size();i++) {
                    var c=next.get(i); String prefix="collection."+i+".";
                    p.setProperty(prefix+"id",c.id().toString()); p.setProperty(prefix+"name",c.name());
                    p.setProperty(prefix+"member.count",Integer.toString(c.members().size()));
                    for(int j=0;j<c.members().size();j++) {
                        var m=c.members().get(j); String mp=prefix+"member."+j+".";
                        p.setProperty(mp+"sha256",m.content().sha256()); p.setProperty(mp+"mode",Integer.toString(m.content().mode()));
                        p.setProperty(mp+"setId",m.location().setId()); p.setProperty(mp+"osuPath",m.location().osuPath());
                    }
                }
                var out=new StringWriter(); p.store(out,"osu!java local collections"); byte[] bytes=out.toString().getBytes(StandardCharsets.UTF_8);
                if(bytes.length>MAX_BYTES) throw new IOException("Collection file limit reached");
                var parent=file.toAbsolutePath().getParent(); Files.createDirectories(parent);
                temporary=Files.createTempFile(parent,".collections-",".tmp"); Files.write(temporary,bytes);
                try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(AtomicMoveNotSupportedException e) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
                diskBytes=bytes;
            }
            snapshot=List.copyOf(next); revision++; status=Status.READY; error=""; return true;
        } catch(IOException | RuntimeException e) { fail(e); return false; }
        finally { if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException e) { System.err.println(e.getMessage()); } }
    }
    private byte[] readBytes() throws IOException {
        if(Files.size(file)>MAX_BYTES) throw new IOException("Collection file limit reached");
        return Files.readAllBytes(file);
    }
    private void load() {
        if(Files.notExists(file)) return;
        try {
            diskBytes=readBytes(); var p=new Properties(); p.load(new StringReader(new String(diskBytes,StandardCharsets.UTF_8)));
            if(!"1".equals(p.getProperty("schemaVersion"))) throw new IOException("Unsupported collection schema");
            int count=count(p,"collection.count",MAX_COLLECTIONS), total=0; var values=new ArrayList<Collection>(); var ids=new HashSet<UUID>();
            for(int i=0;i<count;i++) {
                String prefix="collection."+i+"."; var id=UUID.fromString(p.getProperty(prefix+"id"));
                if(!ids.add(id)) throw new IOException("Duplicate collection ID");
                int n=count(p,prefix+"member.count",MAX_MEMBERS); total+=n;
                if(total>MAX_MEMBERS) throw new IOException("Collection limit reached");
                var members=new ArrayList<Member>();
                for(int j=0;j<n;j++) {
                    String mp=prefix+"member."+j+".";
                    members.add(new Member(new BeatmapContentKey(p.getProperty(mp+"sha256"),Integer.parseInt(p.getProperty(mp+"mode"))),
                            new DifficultyIdentity(p.getProperty(mp+"setId"),p.getProperty(mp+"osuPath"))));
                }
                values.add(new Collection(id,p.getProperty(prefix+"name"),members));
            }
            snapshot=values.stream().sorted(Comparator.comparing(Collection::name).thenComparing(c -> c.id().toString())).toList();
        } catch(NoSuchFileException | NotDirectoryException e) { diskBytes=null; }
        catch(IOException | RuntimeException e) { readable=false; fail(e); }
    }
    private static int count(Properties p,String key,int limit) throws IOException {
        int n=Integer.parseInt(p.getProperty(key)); if(n<0 || n>limit) throw new IOException("Invalid collection count"); return n;
    }
    private static String validName(String name) {
        if(name==null) throw new IllegalArgumentException("Enter a collection name.");
        name=Normalizer.normalize(name.strip(),Normalizer.Form.NFC);
        if(name.isBlank() || name.codePointCount(0,name.length())>80 || name.codePoints().anyMatch(Character::isISOControl)
                || name.chars().anyMatch(c -> Character.isSurrogate((char)c)) && !validSurrogates(name))
            throw new IllegalArgumentException("Use a name of 1–80 characters.");
        return name;
    }
    private static boolean validSurrogates(String name) {
        for(int i=0;i<name.length();i++) if(Character.isSurrogate(name.charAt(i))) {
            if(!Character.isHighSurrogate(name.charAt(i)) || ++i>=name.length() || !Character.isLowSurrogate(name.charAt(i))) return false;
        }
        return true;
    }
    private void fail(Exception e) { status=Status.UNAVAILABLE; error="Could not save or read collections."; System.err.println("Local collections: "+e.getMessage()); }
}

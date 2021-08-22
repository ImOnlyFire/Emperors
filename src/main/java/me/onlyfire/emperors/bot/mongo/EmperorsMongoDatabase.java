package me.onlyfire.emperors.bot.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.lang.Nullable;
import lombok.NonNull;
import me.onlyfire.emperors.bot.exceptions.EmperorException;
import me.onlyfire.emperors.bot.mongo.models.*;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static com.mongodb.client.model.Filters.eq;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class EmperorsMongoDatabase {

    private MongoClient mongoClient;

    private MongoCollection<MongoGroup> groupsCollection;
    private MongoCollection<MongoUser> usersCollection;
    private MongoCollection<MongoSuggestion> suggestionsCollection;

    public void connect(String uri) {
        mongoClient = MongoClients.create(uri);
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

        MongoDatabase database = mongoClient.getDatabase("emperors").withCodecRegistry(pojoCodecRegistry);
        groupsCollection = database.getCollection("groups", MongoGroup.class);
        usersCollection = database.getCollection("users", MongoUser.class);
        suggestionsCollection = database.getCollection("suggestions", MongoSuggestion.class);
    }

    public void registerGroup(Chat chat) {
        if (chat == null) return;
        if (chat.isUserChat()) return;
        MongoGroup loadedGroup = groupsCollection.find(eq("group_id", chat.getId())).first();
        if (loadedGroup != null) return;

        MongoGroup newMongoGroup = new MongoGroup();
        newMongoGroup.setGroupId(chat.getId());
        newMongoGroup.setName(chat.getTitle());
        groupsCollection.insertOne(newMongoGroup);
    }

    public void registerUser(User user) {
        if (user == null) return;
        MongoUser loadedUser = usersCollection.find(eq("user_id", user.getId())).first();
        if (loadedUser != null) return;

        MongoUser newMongoUser = new MongoUser();
        newMongoUser.setUserId(user.getId());
        newMongoUser.setUserName(user.getUserName());
        usersCollection.insertOne(newMongoUser);
    }

    public long createEmperor(@NonNull Chat chat, @NonNull String emperorName, @NonNull String photoId) {
        long before = System.currentTimeMillis();
        MongoGroup loadedGroup = groupsCollection.find(eq("group_id", chat.getId())).first();
        if (loadedGroup == null) throw new EmperorException("Loaded group is non existent");

        MongoEmperor emperor = new MongoEmperor();
        emperor.setName(emperorName);
        emperor.setPhotoId(photoId);
        emperor.setTaken(false);
        loadedGroup.getEmperors().add(emperor);

        updateGroup(loadedGroup);
        return (System.currentTimeMillis() - before);
    }

    public long takeEmperor(@NonNull User user, @NonNull Chat chat, @NonNull MongoEmperor emperor) {
        long before = System.currentTimeMillis();
        MongoUser loadedUser = getMongoUser(user);
        if (loadedUser == null) throw new EmperorException("Loaded user is non existent");
        MongoGroup loadedGroup = groupsCollection.find(eq("group_id", chat.getId())).first();
        if (loadedGroup == null) throw new EmperorException("Loaded group is non existent");

        ZoneId zoneId = ZoneId.of("Europe/Rome");
        ZonedDateTime startDateTime = ZonedDateTime.ofInstant(Instant.now(), zoneId).toLocalDate().atStartOfDay(zoneId);
        ZonedDateTime tomorrowDateTime = startDateTime.plusDays(1);
        long takenTime = tomorrowDateTime.toEpochSecond();

        MongoTakenEmperor takenEmperor = new MongoTakenEmperor();
        takenEmperor.setName(emperor.getName());
        takenEmperor.setGroupId(chat.getId());
        takenEmperor.setTakenTime(takenTime);
        takenEmperor.setTakenById(user.getId());
        takenEmperor.setTakenByName(user.getFirstName());

        int index = loadedGroup.getEmperors().indexOf(emperor);
        emperor.setTaken(true);
        loadedGroup.getEmperors().set(index, emperor);

        loadedUser.getEmperorsTaken().add(takenEmperor);

        updateGroup(loadedGroup);
        updateUser(loadedUser);
        return (System.currentTimeMillis() - before);
    }

    @Nullable
    public MongoTakenEmperor getTakenEmperor(@NonNull Chat chat, @NonNull MongoEmperor emperor) {
        MongoGroup loadedGroup = groupsCollection.find(eq("group_id", chat.getId())).first();
        if (loadedGroup == null) throw new EmperorException("Loaded group is non existent");

        for (MongoUser users : getAllMongoUsers()) {
            for (MongoTakenEmperor takenEmperors : users.getEmperorsTaken()) {
                if (takenEmperors.getName().equals(emperor.getName())) {
                    return takenEmperors;
                }
            }
        }

        return null;
    }

    @Nullable
    public MongoTakenEmperor getTakenEmperor(@NonNull MongoUser user, @NonNull MongoGroup chat, @NonNull MongoEmperor emperor) {
        for (MongoTakenEmperor takenEmperor : user.getEmperorsTaken()) {
            if (takenEmperor.getGroupId().equals(chat.getGroupId()) && takenEmperor.getName().equalsIgnoreCase(emperor.getName())) {
                return takenEmperor;
            }
        }
        return null;
    }

    @Nullable
    public void emitEmperor(@NonNull MongoUser user, @NonNull MongoGroup chat, @NonNull MongoEmperor mongoEmperor) {
        MongoTakenEmperor takenEmperor = getTakenEmperor(user, chat, mongoEmperor);
        if (takenEmperor == null) throw new EmperorException("Loaded emperor is non existent");

        user.getEmperorsTaken().remove(takenEmperor);

        int index = chat.getEmperors().indexOf(mongoEmperor);
        mongoEmperor.setTaken(false);
        chat.getEmperors().set(index, mongoEmperor);

        updateUser(user);
        updateGroup(chat);
    }

    @Nullable
    public MongoTakenEmperor getTakenEmperor(@NonNull MongoUser mongoUser, @NonNull Chat chat, @NonNull MongoEmperor emperor) {
        for (MongoTakenEmperor takenEmperor : mongoUser.getEmperorsTaken()) {
            if (takenEmperor.getGroupId().equals(chat.getId()) && takenEmperor.getName().equalsIgnoreCase(emperor.getName())) {
                return takenEmperor;
            }
        }
        return null;
    }

    public long deleteEmperor(@NonNull Chat chat, @NonNull MongoEmperor emperor) {
        long before = System.currentTimeMillis();
        MongoGroup loadedGroup = groupsCollection.find(eq("group_id", chat.getId())).first();
        if (loadedGroup == null) throw new EmperorException("Loaded group is non existent");

        loadedGroup.getEmperors().remove(emperor);

        updateGroup(loadedGroup);
        return (System.currentTimeMillis() - before);
    }

    public void updateGroup(@NonNull MongoGroup group) {
        Document filterByGradeId = new Document("_id", group.getId());
        FindOneAndReplaceOptions returnDocAfterReplace = new FindOneAndReplaceOptions()
                .returnDocument(ReturnDocument.AFTER);
        groupsCollection.findOneAndReplace(filterByGradeId, group, returnDocAfterReplace);
    }

    public void updateUser(@NonNull MongoUser user) {
        Document filterByGradeId = new Document("_id", user.getId());
        FindOneAndReplaceOptions returnDocAfterReplace = new FindOneAndReplaceOptions()
                .returnDocument(ReturnDocument.AFTER);
        usersCollection.findOneAndReplace(filterByGradeId, user, returnDocAfterReplace);
    }

    @Nullable
    public MongoEmperor getEmperorByName(@NonNull Chat chat, @NonNull String name) {
        MongoGroup loadedGroup = getMongoGroup(chat);
        if (loadedGroup == null) throw new EmperorException("Loaded group is non existent");

        for (MongoEmperor emperor : loadedGroup.getEmperors()) {
            if (emperor.getName().equalsIgnoreCase(name)) {
                return emperor;
            }
        }
        return null;
    }

    @Nullable
    public MongoGroup getMongoGroup(@NonNull Chat chat) {
        return groupsCollection.find(eq("group_id", chat.getId())).first();
    }

    @Nullable
    public MongoUser getMongoUser(@NonNull User user) {
        return usersCollection.find(eq("user_id", user.getId())).first();
    }

    public List<MongoGroup> getAllMongoGroups() {
        return groupsCollection.find().into(new ArrayList<>());
    }

    public List<MongoUser> getAllMongoUsers() {
        return usersCollection.find().into(new ArrayList<>());
    }

    public void close() {
        mongoClient.close();
    }
}

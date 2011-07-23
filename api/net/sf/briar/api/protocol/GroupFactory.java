package net.sf.briar.api.protocol;

public interface GroupFactory {

	Group createGroup(GroupId id, String name, byte[] salt, byte[] publicKey);
}

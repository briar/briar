# Briar REST API

This is a headless Briar peer that exposes a REST API
with an integrated HTTP server instead of a traditional user interface.
You can use this API to script the peer behavior
or to develop your own user interface for it.

## How to use

The REST API peer comes as a `jar` file
and needs a Java Runtime Environment (JRE) that supports at least Java 8.
It currently works on GNU/Linux operating systems, on Windows and on macOS.

To build the `jar` file, you need to specify the combination of architecture and platform:

    $ ./gradlew --configure-on-demand briar-headless:x86LinuxJar
    $ ./gradlew --configure-on-demand briar-headless:aarch64LinuxJar
    $ ./gradlew --configure-on-demand briar-headless:armhfLinuxJar
    $ ./gradlew --configure-on-demand briar-headless:windowsJar
    $ ./gradlew --configure-on-demand briar-headless:x86MacOsJar
    $ ./gradlew --configure-on-demand briar-headless:aarch64MacOsJar

You can start the peer (and its API server) like this:

    $ java -jar briar-headless/build/libs/briar-headless-<platform>-<architecture>.jar

It is possible to put parameters at the end.
Try `--help` for a list of options.

On the first start, it will ask you to create a user account:

    $ java -jar briar-headless-linux-x86_64.jar
    No account found. Let's create one!

    Nickname: testuser
    Password:

After entering a password, it will start up without further output.
Use the `-v` option if you prefer more verbose logging.

By default, Briar creates a folder `~/.briar` where it stores its database and other files.
There you also find the authentication token which is required to interact with the API:

    $ cat ~/.briar/auth_token
    DZbfoUie8sjap7CSDR9y6cgJCojV+xUITTIFbgtAgqk=

You can test that things work as expected by running:

    $ curl -H "Authorization: Bearer DZbfoUie8sjap7CSDR9y6cgJCojV+xUITTIFbgtAgqk=" http://127.0.0.1:7000/v1/contacts
    []

The answer is an empty JSON array, because you don't have any contacts.
Note that the HTTP request sets an `Authorization` header with the bearer token.
A missing or wrong token will result in a `401` response.

To run on macOS you will need to sign the native tor binaries included in the
jar file. To do so, extract the files in `aarch64` or `x86_64` depending on your
system architecture, sign them using `codesign` and replace the original files
in the jar files with the signed ones.

## REST API

### Listing all contacts

`GET /v1/contacts`

Returns a JSON array of contacts:

```json
{
    "author": {
        "formatVersion": 1,
        "id": "y1wkIzAimAbYoCGgWxkWlr6vnq1F8t1QRA/UMPgI0E0=",
        "name": "Test",
        "publicKey": "BDu6h1S02bF4W6rgoZfZ6BMjTj/9S9hNN7EQoV05qUo="
    },
    "contactId": 1,
    "alias" : "A local nickname",
    "handshakePublicKey": "XnYRd7a7E4CTqgAvh4hCxh/YZ0EPscxknB9ZcEOpSzY=",
    "verified": true,
    "lastChatActivity": 1557838312175,
    "connected": false,
    "unreadCount": 7
}
```

Note that the key `alias` isn't guaranteed to be in the response.

### Adding a contact

The first step is to get your own link:

`GET /v1/contacts/add/link`

This returns a JSON object with a `briar://` link that needs to be sent to the contact you want to add
outside of Briar via an external channel.

```json
{
    "link": "briar://wvui4uvhbfv4tzo6xwngknebsxrafainnhldyfj63x6ipp4q2vigy"
}
```

Once you have received the link of your future contact, you can add them
by posting the link together with an arbitrary nickname (or alias):

`POST /v1/contacts/add/pending`

The link and the alias should be posted as a JSON object:

```json
{
    "link": "briar://ddnsyffpsenoc3yzlhr24aegfq2pwan7kkselocill2choov6sbhs",
    "alias": "A nickname for the new contact"
}
```

Adding a pending contact starts the process of adding the contact.
Until it is completed, a pending contact is returned as JSON:

```json
{
    "pendingContactId": "jsTgWcsEQ2g9rnomeK1g/hmO8M1Ix6ZIGWAjgBtlS9U=",
    "alias": "ztatsaajzeegraqcizbbfftofdekclatyht",
    "timestamp": 1557838312175
}
```

Possible errors when adding a pending contact are:

#### 400: Pending contact's link is invalid

```json
{
    "error": "INVALID_LINK"
}
```

#### 400: Pending contact's handshake public key is invalid

```json
{
    "error": "INVALID_PUBLIC_KEY"
}
```

#### 403: A contact with the same handshake public key already exists

This error may be caused by someone attacking the user with the goal
of discovering the contacts of the user.

In the Android client, upon encountering this issue a message dialog
is shown that asks whether the contact and the just added pending contact
are the same person. If that's the case, a message is shown that the
contact already exists and the pending contact isn't added.
If that's not the case and they are two different persons, the Android
client
[shows the following message](https://code.briarproject.org/briar/briar/-/blob/beta-1.2.14/briar-android/src/main/res/values/strings.xml#L271)
when this happens:
> [Alice] and [Bob] sent you the same link.
>
> One of them may be trying to discover who your contacts are.
>
> Don't tell them you received the same link from someone else.

```json
{
    "error": "CONTACT_EXISTS",
    "remoteAuthorName": "Bob"
}
```

#### 403: A pending contact with the same handshake public key already exists

This error, too, may be caused by someone attacking the user with the goal
of discovering the contacts of the user.

Just like above, upon encountering this issue a message dialog is shown in
the Android client that asks whether the contact and the just added pending
contact are the same person. If that's the case, the pending contact gets
updated. If that's not the case and they are two different persons, the
Android client shows the same message as above, warning the user about the
possible attack.

```json
{
    "error": "PENDING_EXISTS",
    "pendingContactId": "jsTgWcsEQ2g9rnomeK1g/hmO8M1Ix6ZIGWAjgBtlS9U=",
    "pendingContactAlias": "Alice"
}
```
-----------

Before users can send messages to contacts, they become pending contacts.
In this state Briar still needs to do some work in the background (e.g.
spinning up a dedicated hidden service and letting the contact connect to it).
Pending contacts aren't shown in the Android's client contact list.
Note that the `pendingContactId` differs from the `authorId` the contact will get later.
The `pendingContactId` is the hash of their public handshake key, so it is the
same if another device is trying to add the same contact.

It is possible to get a list of all pending contacts:

`GET /v1/contacts/add/pending`

This will return a JSON array of pending contacts and their states:

```json
{
    "pendingContact": {
        "pendingContactId": "jsTgWcsEQ2g9rnomeK1g/hmO8M1Ix6ZIGWAjgBtlS9U=",
        "alias": "ztatsaajzeegraqcizbbfftofdekclatyht",
        "timestamp": 1557838312175
    },
    "state": "adding_contact"
}
```

The state can be one of these values:

  * `waiting_for_connection`: Briar is still waiting to establish a connection
  via the dedicated Tor hidden service(s). Each contact creates one and whoever
  connects first wins. Making the hidden services available and establishing a
  connection to them can take some time.
  * `offline`: Briar went offline before the contact could be added.
  * `connecting`: Briar made a connection and can now start the process of
  adding the contact.
  * `adding_contact`: The contact will be added. Once this is complete the
  pending contact will be removed and replaced by a "real" contact.
  * `failed`: Briar tried for two days to connect, but couldn't get a
  connection, so it will stop trying. The pending contact will stick around as
  failed until removed.

If you want to be informed about state changes,
you can use the Websocket API (below) to listen for events.

The following events are relevant here:

  * `PendingContactAddedEvent`
  * `PendingContactStateChangedEvent`
  * `PendingContactRemovedEvent`
  * `ContactAddedEvent` (when the pending contact becomes an actual contact)

To remove a pending contact and abort the process of adding it:

`DELETE /v1/contacts/add/pending`

The `pendingContactId` of the pending contact to delete
needs to be provided in the request body as follows:

```json
{
    "pendingContactId": "jsTgWcsEQ2g9rnomeK1g/hmO8M1Ix6ZIGWAjgBtlS9U="
}
```

Note that it's also possible to add contacts nearby via Bluetooth/Wifi or
introductions. In these cases contacts omit the `pendingContact` state and
directly become `contact`s.

### Changing alias of a contact

`PUT /v1/contacts/{contactId}/alias`

The alias should be posted as a JSON object:

```json
{
    "alias": "A nickname for the new contact"
}
```

### Removing a contact

`DELETE /v1/contacts/{contactId}`

The `{contactId}` is the `contactId` of the contact (`1` in the example above).
It returns with a status code `200`, if removal was successful.

### Listing all private messages

`GET /v1/messages/{contactId}`

The `{contactId}` is the `contactId` of the contact (`1` in the example above).
It returns a JSON array of private messages:

```json
{
    "contactId": 1,
    "groupId": "oRRvCri85UE2XGcSloAKt/u8JDcMkmDc26SOMouxr4U=",
    "id": "ZGDrlpCxO9v7doO4Bmijh95QqQDykaS4Oji/mZVMIJ8=",
    "local": true,
    "read": true,
    "seen": true,
    "sent": true,
    "text": "test",
    "timestamp": 1537376633850,
    "type": "PrivateMessage"
}
```

If `local` is `true`, the message was sent by the Briar peer instead of its remote contact.

A message is `read` when the local user has read it i.e. it was displayed on their screen.
This only makes sense for incoming messages (which are not `local`).
`sent` and `seen` are only useful for outgoing (`local`) messages.
`sent` means that we offered the message to the contact (one tick) and `seen` (two ticks)
means that they confirmed the delivery of the message.

Attention: There can messages of other `type`s where the message `text` is `null`.

### Writing a private message

`POST /v1/messages/{contactId}`

The text of the message should be posted as JSON:

```json
{
  "text": "Hello World!"
}
```

### Marking private messages as read

`POST /v1/messages/{contactId}/read`

The `messageId` of the message to be marked as read
needs to be provided in the request body as follows:

```json
{
    "messageId": "+AIMMgOCPFF8HDEhiEHYjbfKrg7v0G94inKxjvjYzA8="
}
```

### Deleting all private messages

`DELETE /v1/messages/{contactId}/all`

It returns with a status code `200`, if removal was successful.

### Listing blog posts

`GET /v1/blogs/posts`

Returns a JSON array of blog posts:

```json
{
    "author": {
        "formatVersion": 1,
        "id": "VNKXkaERPpXmZuFbHHwYT6Qc148D+KNNxQ4hwtx7Kq4=",
        "name": "Test",
        "publicKey": "NbwpQWjS3gHMjjDQIASIy/j+bU6NRZnSRT8X8FKDoN4="
    },
    "authorStatus": "ourselves",
    "id": "X1jmHaYfrX47kT5OEd0OD+p/bptyR92IvuOBYSgxETM=",
    "parentId": null,
    "read": true,
    "rssFeed": false,
    "text": "Test Post Content",
    "timestamp": 1535397886749,
    "timestampReceived": 1535397886749,
    "type": "post"
}
```

`authorStatus` indicates what we know about the author of a blog post. Its possible values
are:

  * `none`: This is only used for RSS feed blog posts where Briar can't link
  the author to one of its contacts.
  * `unknown`: The author has broadcasted their identity but we don't know them.
  * `unverified`: The author is one of our contacts but we didn't verify their
  identity key. This happens for contacts added remotely or via introduction.
  * `verified`: The author is one of our contacts and we verified their identity key.
  * `ourselves`: The user is the author of the blog post.

### Writing a blog post

`POST /v1/blogs/posts`

The text of the blog post should be posted as JSON:

```json
{
  "text": "Hello Blog World!"
}
```

## Websocket API

The Briar peer uses a websocket to notify a connected API client about new events.

`WS /v1/ws`

Immediately after making the connection,
you must send the authentication token as a message to the websocket.
If you fail to do this, you will not receive messages on that socket.

In JavaScript, it would look like this:

```javascript
var token = "DZbfoUie8sjap7CSDR9y6cgJCojV+xUITTIFbgtAgqk=";
var socket = new WebSocket("ws://localhost:7000/v1/ws");
socket.onopen = function(event) { socket.send(token); };
socket.onmessage = function(event) { console.log(event.data); }
```

### Receiving new private messages

When the Briar peer receives a new private message,
it will send a JSON object to connected websocket clients:

```json
{
    "data": {
        "contactId": 1,
        "groupId": "oRRvCri85UE2XGcSloAKt/u8JDcMkmDc26SOMouxr4U=",
        "id": "JBc+ogQIok/yr+7XtxN2iQgNfzw635mHikNaP5QOEVs=",
        "local": false,
        "read": false,
        "seen": false,
        "sent": false,
        "text": "Test Message",
        "timestamp": 1537389146088,
        "type": "PrivateMessage"
    },
    "name": "ConversationMessageReceivedEvent",
    "type": "event"
}
```

Note that the JSON object in `data` is exactly what the REST API returns
when listing private messages.

### A new contact was added

This means that a new contact was either added directly or that it has left
the pending state.

```json
{
    "data": {
        "contactId": 1,
        "verified": false
    },
    "name": "ContactAddedEvent",
    "type": "event"
}
```

### A pending contact was added

This means that a new `pendingContact` was added and Briar will try to add it
as a real `contact`.

```json
{
    "data": {
        "pendingContact": {
            "pendingContactId": "jsTgWcsEQ2g9rnomeK1g/hmO8M1Ix6ZIGWAjgBtlS9U=",
            "alias": "ztatsaajzeegraqcizbbfftofdekclatyht",
            "timestamp": 1557838312175
        }
    },
    "name": "PendingContactAddedEvent",
    "type": "event"
}

```
### A pending contact changed its state

```json
{
    "data": {
        "pendingContactId": "YqKjsczCuxScXohb5+RAYtFEwK71icoB4ldztV2gh7M=",
        "state": "waiting_for_connection"
    },
    "name": "PendingContactStateChangedEvent",
    "type": "event"
}
```

For a list of valid states, please see the section on adding contacts above.

### A pending contact was removed

This can happen when e.g. the user removed the pending contact manually. Briar
will no longer work on making this `pendingContact` become `contact`.

```json
{
    "data": {
        "pendingContactId": "YqKjsczCuxScXohb5+RAYtFEwK71icoB4ldztV2gh7M="
    },
    "name": "PendingContactRemovedEvent",
    "type": "event"
}
```

### A contact connected or disconnected

When Briar establishes a connection to a contact (the contact comes online),
it sends a `ContactConnectedEvent`.
When the last connection is lost (the contact goes offline), it sends a `ContactDisconnectedEvent`.

```json
{
    "data": {
        "contactId": 1
    },
    "name": "ContactConnectedEvent",
    "type": "event"
}
```

### A message was sent

When Briar sent a message to a contact, it sends a `MessagesSentEvent`. This is indicated in Briar
by showing one tick next to the message.

```json
{
    "data": {
        "contactId": 1,
        "messageIds": [
            "+AIMMgOCPFF8HDEhiEHYjbfKrg7v0G94inKxjvjYzA8="
        ]
    },
    "name": "MessagesSentEvent",
    "type": "event"
}
```

### A message was acknowledged

When a contact acknowledges that they received a message, Briar sends a `MessagesAckedEvent`.
This is indicated in Briar by showing two ticks next to the message.

```json
{
    "data": {
        "contactId": 1,
        "messageIds": [
            "+AIMMgOCPFF8HDEhiEHYjbfKrg7v0G94inKxjvjYzA8="
        ]
    },
    "name": "MessagesAckedEvent",
    "type": "event"
}
```

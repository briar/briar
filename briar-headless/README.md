# Briar REST API

This is a headless Briar peer that exposes a REST API
with an integrated HTTP server instead of a traditional user interface.
You can use this API to script the peer behavior
or to develop your own user interface for it.

## How to use

The REST API peer comes as a `jar` file
and needs a Java Runtime Environment (JRE) that supports at least Java 8.
It currently works only on GNU/Linux operating systems.

To build the `jar` file, you can do this:

    $ ./gradlew --configure-on-demand briar-headless:jar

You can start the peer (and its API server) like this:

    $ java -jar briar-headless/build/libs/briar-headless.jar

It is possible to put parameters at the end.
Try `--help` for a list of options.

On the first start, it will ask you to create a user account:

    $ java -jar briar-headless.jar
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
    "verified": true
}
```

### Adding a contact

The first step is to get your own link:

`GET /v1/contacts/add/link`

Returns a JSON object with a `briar://` link that needs to be sent to the contact you want to add
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

This starts the process of adding the contact.
Until it is completed, a pending contact is returned as JSON:

```json
{
    "pendingContactId": "jsTgWcsEQ2g9rnomeK1g/hmO8M1Ix6ZIGWAjgBtlS9U=",
    "alias": "ztatsaajzeegraqcizbbfftofdekclatyht",
    "timestamp": 1557838312175
}
```

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

  * `waiting_for_connection`
  * `connected`
  * `adding_contact`
  * `failed`

If you want to be informed about state changes,
you can use the Websocket API (below) to listen for events.

The following events are relevant here:

  * `PendingContactAddedEvent`
  * `PendingContactStateChangedEvent`
  * `PendingContactRemovedEvent`
  * `ContactAddedRemotelyEvent` (when the pending contact becomes an actual contact)

To remove a pending contact and abort the process of adding it:

`DELETE /v1/contacts/add/pending`

The `pendingContactId` of the pending contact to delete
needs to be provided in the request body as follows:

```json
{
    "pendingContactId": "jsTgWcsEQ2g9rnomeK1g/hmO8M1Ix6ZIGWAjgBtlS9U="
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

Attention: There can messages of other `type`s where the message `text` is `null`.

### Writing a private message

`POST /v1/messages/{contactId}`

The text of the message should be posted as JSON:

```json
{
  "text": "Hello World!"
}
```

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

### A new contact was added remotely

When the Briar peer adds a new contact remotely,
it will send a JSON object representing the new contact to connected websocket clients:

```json
{
    "data": {
        "contact": {
            "author": {
                "formatVersion": 1,
                "id": "y1wkIzAimAbYoCGgWxkWlr6vnq1F8t1QRA/UMPgI0E0=",
                "name": "Test",
                "publicKey": "BDu6h1S02bF4W6rgoZfZ6BMjTj/9S9hNN7EQoV05qUo="
            },
            "contactId": 1,
            "alias" : "A local nickname",
            "handshakePublicKey": "XnYRd7a7E4CTqgAvh4hCxh/YZ0EPscxknB9ZcEOpSzY=",
            "verified": true
        }
    },
    "name": "ContactAddedRemotelyEvent",
    "type": "event"
}
```

### A pending contact was added

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
        "pendingContactId":"YqKjsczCuxScXohb5+RAYtFEwK71icoB4ldztV2gh7M=",
        "state":"waiting_for_connection"
    },
    "name": "PendingContactStateChangedEvent",
    "type": "event"
}
```

For a list of valid states, please see the section on adding contacts above.

### A pending contact was removed

```json
{
    "data": {
        "pendingContactId": "YqKjsczCuxScXohb5+RAYtFEwK71icoB4ldztV2gh7M="
    },
    "name": "PendingContactRemovedEvent",
    "type": "event"
}
```

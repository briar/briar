# Briar REST API

This is a headless Briar client that exposes a REST API
with an integrated HTTP server instead of a traditional user interface.
You can use this API to script the client behavior
or to develop your own user interface for it.

## How to use

The REST API client comes as a `jar` file
and needs a Java Runtime Environment (JRE) that supports at least Java 8.
It currently works only on GNU/Linux operating systems.

You can start the client (and its API server) like this:

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
Note you the HTTP request sets an `Authorization` header with the bearer token.
A missing or wrong token will result in a `401` response.

## REST API

### Listing all contacts

`GET /v1/contacts`

Returns a JSON array of contacts:

```json
{
    "author": {
        "id": "y1wkIzAimAbYoCGgWxkWlr6vnq1F8t1QRA/UMPgI0E0=",
        "name": "Test",
        "publicKey": "BDu6h1S02bF4W6rgoZfZ6BMjTj/9S9hNN7EQoV05qUo="
    },
    "contactId": 1,
    "verified": true
}
```

### Adding a contact

*Not yet implemented*

The only workaround is to add a contact to the Briar app running on a rooted Android phone
and then move its database (and key files) to the headless client.

### Listing all private messages

`GET /messages/{contactId}`

The `{contactId}` is the `contactId` of the contact (`1` in the example above).
It returns a JSON array of private messages:

```json
{
    "body": "test",
    "contactId": 1,
    "groupId": "oRRvCri85UE2XGcSloAKt/u8JDcMkmDc26SOMouxr4U=",
    "id": "ZGDrlpCxO9v7doO4Bmijh95QqQDykaS4Oji/mZVMIJ8=",
    "local": true,
    "read": true,
    "seen": true,
    "sent": true,
    "timestamp": 1537376633850,
    "type": "org.briarproject.briar.api.messaging.PrivateMessageHeader"
}
```

If `local` is `true`, the message was sent by the Briar client instead of its remote contact.

Attention: There can messages of other `type`s where the message `body` is `null`.

### Writing a private message

`POST /messages/{contactId}`

The text of the message should be included in the form parameter `text`.

### Listing blog posts

`GET /v1/blogs/posts`

Returns a JSON array of blog posts:

```json
{
    "author": {
        "id": "VNKXkaERPpXmZuFbHHwYT6Qc148D+KNNxQ4hwtx7Kq4=",
        "name": "Test",
        "publicKey": "NbwpQWjS3gHMjjDQIASIy/j+bU6NRZnSRT8X8FKDoN4="
    },
    "authorStatus": "ourselves",
    "body": "Test Post Content",
    "id": "X1jmHaYfrX47kT5OEd0OD+p/bptyR92IvuOBYSgxETM=",
    "parentId": null,
    "read": true,
    "rssFeed": false,
    "timestamp": 1535397886749,
    "timestampReceived": 1535397886749,
    "type": "post"
}
```

### Writing a blog post

`POST /v1/blogs/posts`

The text of the blog post should be included in the form parameter `text`.

## Websocket API

The Briar client uses a websocket to notify a connected API client about new events.

`WS /v1/ws`

The authentication token needs to be provided with basic auth as the username
while the password can be left empty.

You can test connecting to the websocket with curl:

    $ curl --no-buffer \
           --header "Connection: Upgrade" \
           --header "Upgrade: websocket" \
           --header "Sec-WebSocket-Key: SGVsbG8sIHdvcmxkIQ==" \
           --header "Sec-WebSocket-Version: 13" \
           http://DZbfoUie8sjap7CSDR9y6cgJCojV+xUITTIFbgtAgqk=@127.0.0.1:7000/v1/ws

The headers are only required when testing with curl.
Your websocket client will most likely add these headers automatically.

### Receiving new private messages

When the Briar client receives a new private message,
it will send a JSON object to connected websocket clients:

```json
{
    "data": {
        "body": "Test Message",
        "contactId": 1,
        "groupId": "oRRvCri85UE2XGcSloAKt/u8JDcMkmDc26SOMouxr4U=",
        "id": "JBc+ogQIok/yr+7XtxN2iQgNfzw635mHikNaP5QOEVs=",
        "local": false,
        "read": false,
        "seen": false,
        "sent": false,
        "timestamp": 1537389146088,
        "type": "org.briarproject.briar.api.messaging.PrivateMessageHeader"
    },
    "name": "org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent",
    "type": "event"
}
```

Note that the JSON object in `data` is exactly what the REST API returns
when listing private messages.

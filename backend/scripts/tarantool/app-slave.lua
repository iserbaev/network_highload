-- scripts/app.lua # need to be set on different port/file if not using docker-compose
-- for multi-master, you should not use counter data type or it would be out of sync/conflict
-- so it's better to use master-slave (2 read_only replica)

package.path = '/opt/scripts/?.lua;' .. package.path

local console = require('console')

box.cfg {
    listen = 3301,
    replication = {
        'replicator:password@tt1:3301', -- master URI
        'replicator:password@tt2:3301', -- replica 1 URI
        'replicator:password@tt3:3301', -- replica 2 URI
    },
    read_only = true -- set to true for replica 1 and 2 if you want master-slave
}

console.listen('localhost:33013')

box.once("schema", function()
    box.schema.user.create('replicator', { password = 'password' })
    box.schema.user.grant('replicator', 'replication') -- grant replication role

    -- conversation space migration
    box.execute([[CREATE TABLE IF NOT EXISTS conversation_log
            (
                id                               UUID        DEFAULT gen_random_uuid() NOT NULL,
                participant                      UUID                                  NOT NULL,
                private_conversation             BOOLEAN                               NOT NULL,
                private_conversation_participant UUID,
                created_at                       TIMESTAMPTZ DEFAULT now()             NOT NULL,
                PRIMARY KEY (id, participant)
            )
    ]])

    -- private_message_log space migration
    box.execute([[CREATE TABLE IF NOT EXISTS private_message_log
        (
            conversation_id    UUID                                                          NOT NULL,
            conversation_index BIGINT      DEFAULT nextval('private_conversation_index_seq') NOT NULL,
            message_from       UUID                                                          NOT NULL,
            message_to         UUID                                                          NOT NULL,
            message            TEXT                                                          NOT NULL,
            created_at         TIMESTAMPTZ DEFAULT NOW()                                     NOT NULL,
            PRIMARY KEY (conversation_id, conversation_index, message_from)
        )
    ]])

    -- group_message_log space migrations
    box.execute([[CREATE TABLE IF NOT EXISTS group_message_log
        (
            conversation_id    UUID                                                        NOT NULL,
            conversation_index BIGINT      DEFAULT nextval('group_conversation_index_seq') NOT NULL,
            sender             UUID                                                        NOT NULL,
            message            TEXT                                                        NOT NULL,
            created_at         TIMESTAMPTZ DEFAULT NOW()                                   NOT NULL,
            PRIMARY KEY (conversation_id, conversation_index, sender)
        )
    ]])

    print('box.once executed on slave')
end)

-- run http server
local http_port = os.getenv('TNT_HTTP_PORT') or '8080'
local server = require('http.server').new(nil, http_port, { charset = "utf8" }) -- listen *:8080

local handlers = require('http-handlers')

server:route({ path = '/test' }, handlers.test_handler)

server:start()

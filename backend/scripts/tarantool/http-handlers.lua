local exports = {}

json = require('json')
log = require('log')
datetime = require('datetime')
uuid = require('uuid')

exports.test_handler = function(req)
    -- req is a Request object
    -- resp is a Response object
    local resp = req:render({ text = req.method .. ' ' .. req.path })
    resp.headers['x-test-header'] = 'test';
    resp.status = 201
    return resp
end

exports.add_dialog = function(req)
    -- req is a Request object
    -- resp is a Response object
    local dialog = req:json()
    log.info("Received dialog")
    log.info(dialog)

    box.space.private_message_log:insert {
        uuid.fromstr(dialog.conversation_id),
        tonumber(dialog.conversation_index),
        uuid.fromstr(dialog.message_from),
        uuid.fromstr(dialog.message_to),
        dialog.message,
        datetime.new()
    }
    log.info("Dialog saved success")

    local resp = req:render { text = "" }
    resp.status = 201
    return resp
end

exports.list_dialogs = function(req)
    local conversation_id = req:stash('conversation_id')
    log.info("Received request to list dialogs")
    log.info(conversation_id)
    local dialogs = box.space.private_message_log.index.primary:select(uuid.fromstr(conversation_id))

    local resp = req:render({json = json.encode(dialogs)})
    resp.status = 200
    return resp
end

exports.all_dialogs = function(req)
    log.info("Received request to list all dialogs")
    local dialogs = box.space.private_message_log.index.primary:select()

    local resp = req:render({json = json.encode(dialogs)})
    resp.status = 200
    return resp
end

return exports
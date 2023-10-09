local exports = {}

exports.test_handler = function(req)
    -- req is a Request object
    -- resp is a Response object
    local resp = req:render({text = req.method..' '..req.path })
    resp.headers['x-test-header'] = 'test';
    resp.status = 201
    return resp
end

--exports.add_dialog = function(message_from, message_to, message)
--    box.space.private_message_log.insert()
--
--    box.space.conversation_log.select()
--
--    if err ~= nil then
--        log.error(err)
--        return nil
--    end
--
--    return result
--end
--
--exports.list_dialogs = function(user_id)
--    local bucket_id = vshard.router.bucket_id_mpcrc32(login)
--
--    local user = vshard.router.callbro(bucket_id, 'box.space.user_info:get', {login})
--    return user
--end

return exports
-- 校验、错误计数与消费在同一脚本内完成，避免并发重复使用。
local digest = redis.call('HGET', KEYS[1], 'digest')
if not digest then
    return 0
end
if digest ~= ARGV[1] then
    local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
    if attempts >= 5 then
        redis.call('DEL', KEYS[1])
    end
    return 0
end
redis.call('DEL', KEYS[1])
return 1

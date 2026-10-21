-- 同一账号同一用途每 60 秒发送一次，所有用途合计每小时最多发送 10 次。
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end
local count = tonumber(redis.call('GET', KEYS[2]) or '0')
if count >= 10 then
    return -1
end
redis.call('SET', KEYS[1], '1', 'EX', 60)
local total = redis.call('INCR', KEYS[2])
if total == 1 then
    redis.call('EXPIRE', KEYS[2], 3600)
end
redis.call('HSET', KEYS[3], 'digest', ARGV[1], 'attempts', 0)
redis.call('EXPIRE', KEYS[3], 300)
return 1

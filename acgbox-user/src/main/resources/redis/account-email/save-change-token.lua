-- 同一账号、版本和原邮箱只保留最近一次换绑凭证，写入与过期时间保持原子性。
redis.call('HSET', KEYS[1], 'digest', ARGV[1])
redis.call('EXPIRE', KEYS[1], 300)
return 1

SELECT create_distributed_table('balance_commands_log', 'transaction_id');
SELECT create_distributed_table('balance_events_log', 'account_id');

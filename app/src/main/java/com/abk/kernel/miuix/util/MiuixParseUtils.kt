package com.abk.kernel.miuix.util

/**
 * 将逗号/空格/换行/制表符分隔的字符串转换为整数列表，
 * 过滤掉无法解析为整数的条目。
 *
 * @param input 输入字符串
 * @return 解析出的整数列表，不可解析的条目被忽略
 */
fun parseIntList(input: String): List<Int> =
    input.split(',', ' ', '\n', '\t')
        .mapNotNull { it.trim().toIntOrNull() }
        .distinct()

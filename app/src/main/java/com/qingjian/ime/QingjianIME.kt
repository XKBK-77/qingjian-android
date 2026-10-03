package com.qingjian.ime

import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 青简输入法主服务
 * 核心理念：在输入拼音时，候选词旁显示英文译词，让语言学习自然发生
 */
class QingjianIME : InputMethodService() {

    // 当前输入的拼音
    private var composingPinyin = StringBuilder()

    // 候选词列表
    private var candidates: List<String> = emptyList()

    // 视图引用
    private var inputView: View? = null
    private var composingTextView: TextView? = null
    private var candidateList: LinearLayout? = null
    private var candidateScroll: HorizontalScrollView? = null

    // 是否为中文输入模式
    private var isChineseMode = true

    // 是否大写
    private var isUpperCase = false

    override fun onCreate() {
        super.onCreate()
    }

    override fun onCreateInputView(): View {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.input_view, null)

        composingTextView = view.findViewById(R.id.composingText)
        candidateList = view.findViewById(R.id.candidateList)
        candidateScroll = view.findViewById(R.id.candidateScroll)

        // 加载键盘
        val keyboardContainer = view.findViewById<LinearLayout>(R.id.keyboardContainer)
        val keyboard = inflater.inflate(R.layout.keyboard, keyboardContainer, false)
        keyboardContainer.addView(keyboard)

        // 设置按键监听
        setupKeyListeners(keyboard)

        inputView = view
        return view
    }

    private fun setupKeyListeners(keyboard: View) {
        // 遍历所有按钮
        val buttons = findAllButtons(keyboard)
        for (btn in buttons) {
            btn.setOnClickListener {
                val tag = btn.tag?.toString() ?: return@setOnClickListener
                handleKey(tag)
            }
        }
    }

    private fun findAllButtons(view: View): List<Button> {
        val result = mutableListOf<Button>()
        if (view is Button) {
            result.add(view)
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(findAllButtons(view.getChildAt(i)))
            }
        }
        return result
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return

        when {
            key.length == 1 && key[0].isLetter() -> {
                // 字母键
                if (isChineseMode) {
                    // 中文模式：添加到拼音
                    val ch = if (isUpperCase) key.uppercase() else key
                    composingPinyin.append(ch.lowercase())
                    updateCandidates()
                    isUpperCase = false
                    updateShiftKey()
                } else {
                    // 英文模式：直接上屏
                    val ch = if (isUpperCase) key.uppercase() else key.lowercase()
                    ic.commitText(ch, 1)
                    isUpperCase = false
                    updateShiftKey()
                }
            }
            key == "space" -> {
                if (composingPinyin.isNotEmpty() && candidates.isNotEmpty()) {
                    // 选择第一个候选词
                    selectCandidate(0)
                } else {
                    ic.commitText(" ", 1)
                }
            }
            key == "delete" -> {
                if (composingPinyin.isNotEmpty()) {
                    composingPinyin.deleteCharAt(composingPinyin.length - 1)
                    updateCandidates()
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            key == "enter" -> {
                if (composingPinyin.isNotEmpty()) {
                    // 上屏当前拼音（作为英文）
                    ic.commitText(composingPinyin.toString(), 1)
                    clearComposing()
                } else {
                    // 发送回车
                    val action = currentInputEditorInfo?.imeOptions ?: 0
                    when {
                        action and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 -> {
                            ic.commitText("\n", 1)
                        }
                        else -> {
                            val enterAction = action and EditorInfo.IME_MASK_ACTION
                            when (enterAction) {
                                EditorInfo.IME_ACTION_DONE,
                                EditorInfo.IME_ACTION_GO,
                                EditorInfo.IME_ACTION_NEXT,
                                EditorInfo.IME_ACTION_SEARCH,
                                EditorInfo.IME_ACTION_SEND -> {
                                    ic.performEditorAction(enterAction)
                                }
                                else -> {
                                    ic.commitText("\n", 1)
                                }
                            }
                        }
                    }
                }
            }
            key == "shift" -> {
                isUpperCase = !isUpperCase
                updateShiftKey()
            }
            key == "lang" -> {
                isChineseMode = !isChineseMode
                clearComposing()
                updateLangKey()
            }
            key == "symbols" -> {
                // 切换到符号键盘（简化处理：输入常用标点）
                showSymbols()
            }
        }
    }

    private fun showSymbols() {
        val ic = currentInputConnection ?: return
        // 简化：直接上屏一个常用标点列表，用户点击选择
        // 这里简化为输入句号
        ic.commitText("。", 1)
    }

    private fun updateShiftKey() {
        val keyboard = inputView?.findViewById<LinearLayout>(R.id.keyboardContainer)
            ?.getChildAt(0) as? View ?: return
        val buttons = findAllButtons(keyboard)
        for (btn in buttons) {
            val tag = btn.tag?.toString()
            if (tag == "shift") {
                btn.text = if (isUpperCase) "⇧" else "⇧"
                btn.isPressed = isUpperCase
            } else if (tag != null && tag.length == 1 && tag[0].isLetter()) {
                btn.text = if (isUpperCase) tag.uppercase() else tag.lowercase()
            }
        }
    }

    private fun updateLangKey() {
        val keyboard = inputView?.findViewById<LinearLayout>(R.id.keyboardContainer)
            ?.getChildAt(0) as? View ?: return
        val buttons = findAllButtons(keyboard)
        for (btn in buttons) {
            if (btn.tag?.toString() == "lang") {
                btn.text = if (isChineseMode) "中" else "EN"
            }
        }
    }

    private fun updateCandidates() {
        val pinyin = composingPinyin.toString()

        // 更新拼音显示
        if (pinyin.isNotEmpty()) {
            composingTextView?.visibility = View.VISIBLE
            composingTextView?.text = pinyin
        } else {
            composingTextView?.visibility = View.GONE
        }

        // 获取候选词
        candidates = if (pinyin.isNotEmpty()) {
            PinyinEngine.getCandidates(pinyin)
        } else {
            emptyList()
        }

        // 渲染候选词
        renderCandidates()
    }

    private fun renderCandidates() {
        candidateList?.removeAllViews()

        if (candidates.isEmpty()) {
            candidateScroll?.visibility = View.GONE
            return
        }

        candidateScroll?.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)

        for (i in candidates.indices) {
            val word = candidates[i]
            val itemView = inflater.inflate(R.layout.candidate_item, candidateList, false)

            val wordView = itemView.findViewById<TextView>(R.id.candidateWord)
            val transView = itemView.findViewById<TextView>(R.id.candidateTranslation)

            wordView.text = word

            // 查找并显示英文译词
            val translation = TranslationDictionary.translate(word)
            if (translation != null) {
                transView.text = translation
                transView.visibility = View.VISIBLE
            } else {
                transView.visibility = View.GONE
            }

            // 点击候选词上屏
            itemView.setOnClickListener {
                selectCandidate(i)
            }

            candidateList?.addView(itemView)
        }

        // 滚动到开头
        candidateScroll?.post {
            candidateScroll?.scrollTo(0, 0)
        }
    }

    private fun selectCandidate(index: Int) {
        if (index < 0 || index >= candidates.size) return
        val ic = currentInputConnection ?: return
        val word = candidates[index]
        ic.commitText(word, 1)
        clearComposing()
    }

    private fun clearComposing() {
        composingPinyin.setLength(0)
        candidates = emptyList()
        composingTextView?.visibility = View.GONE
        candidateList?.removeAllViews()
        candidateScroll?.visibility = View.GONE
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clearComposing()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        clearComposing()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 处理物理键盘
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return super.onKeyDown(keyCode, event)
        }
        return false
    }
}

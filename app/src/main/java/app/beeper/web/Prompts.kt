package app.beeper.web

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import java.util.Calendar

/**
 * What a page asks the user (alert, confirm, `<select>`, date fields, file upload, HTTP login). Gecko
 * draws none of these itself. Saving logins, cards and addresses is refused: the app keeps none.
 * [pickFiles] opens Android's file picker and hands back the chosen files (none when cancelled).
 */
class Prompts(
    private val activity: Activity,
    private val pickFiles: (Intent, (List<Uri>) -> Unit) -> Unit,
) : PromptDelegate {
    private fun dialog() = AlertDialog.Builder(activity)

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        dialog().setTitle(prompt.title).setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        var answered = false
        dialog().setTitle(prompt.title).setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok) { _, _ -> answered = true; result.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE)) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> answered = true; result.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)) }
            .setOnDismissListener { if (!answered) result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val input = EditText(activity).apply { setText(prompt.defaultValue) }
        var answered = false
        dialog().setTitle(prompt.title).setMessage(prompt.message).setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ -> answered = true; result.complete(prompt.confirm(input.text.toString())) }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { if (!answered) result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val user = EditText(activity).apply { hint = "Identifiant"; setText(prompt.authOptions.username) }
        val password = EditText(activity).apply { hint = "Mot de passe"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; addView(user); addView(password) }
        var answered = false
        dialog().setTitle(prompt.title ?: "Connexion").setMessage(prompt.message).setView(padded(box))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                answered = true
                result.complete(prompt.confirm(user.text.toString(), password.text.toString()))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { if (!answered) result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        // Option groups are flattened: the group label is not selectable.
        val choices = prompt.choices.flatMap { c -> if (c.items != null) c.items!!.toList() else listOf(c) }.filter { !it.separator }
        val labels = choices.map { it.label }.toTypedArray()
        var answered = false
        val builder = dialog().setTitle(prompt.title ?: prompt.message)
        if (prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
            val checked = choices.map { it.selected }.toBooleanArray()
            builder.setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    answered = true
                    result.complete(prompt.confirm(choices.filterIndexed { i, _ -> checked[i] }.toTypedArray()))
                }
        } else {
            builder.setSingleChoiceItems(labels, choices.indexOfFirst { it.selected }) { d, i ->
                if (choices[i].disabled) return@setSingleChoiceItems
                answered = true
                result.complete(prompt.confirm(choices[i]))
                d.dismiss()
            }
        }
        builder.setOnDismissListener { if (!answered) result.complete(prompt.dismiss()) }.show()
        return result
    }

    override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val now = Calendar.getInstance()
        var answered = false
        when (prompt.type) {
            PromptDelegate.DateTimePrompt.Type.DATE -> {
                val parts = prompt.defaultValue?.split('-')?.mapNotNull { it.toIntOrNull() }
                val (y, m, d) = if (parts?.size == 3) Triple(parts[0], parts[1] - 1, parts[2]) else Triple(now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH])
                DatePickerDialog(activity, { _, year, month, day ->
                    answered = true
                    result.complete(prompt.confirm("%04d-%02d-%02d".format(year, month + 1, day)))
                }, y, m, d).apply { setOnDismissListener { if (!answered) result.complete(prompt.dismiss()) } }.show()
            }
            PromptDelegate.DateTimePrompt.Type.TIME -> {
                val parts = prompt.defaultValue?.split(':')?.mapNotNull { it.toIntOrNull() }
                val (h, min) = if (parts != null && parts.size >= 2) parts[0] to parts[1] else now[Calendar.HOUR_OF_DAY] to now[Calendar.MINUTE]
                TimePickerDialog(activity, { _, hour, minute ->
                    answered = true
                    result.complete(prompt.confirm("%02d:%02d".format(hour, minute)))
                }, h, min, true).apply { setOnDismissListener { if (!answered) result.complete(prompt.dismiss()) } }.show()
            }
            // Month, week and date-time fields: typed as text, in the format the page expects.
            else -> {
                val input = EditText(activity).apply {
                    setText(prompt.defaultValue)
                    hint = when (prompt.type) {
                        PromptDelegate.DateTimePrompt.Type.MONTH -> "AAAA-MM"
                        PromptDelegate.DateTimePrompt.Type.WEEK -> "AAAA-Wss"
                        else -> "AAAA-MM-JJTHH:MM"
                    }
                }
                dialog().setTitle(prompt.title).setView(padded(input))
                    .setPositiveButton(android.R.string.ok) { _, _ -> answered = true; result.complete(prompt.confirm(input.text.toString())) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener { if (!answered) result.complete(prompt.dismiss()) }
                    .show()
            }
        }
        return result
    }

    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(prompt.mimeTypes?.singleOrNull()?.takeIf { it.isNotBlank() } ?: "*/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE)
        prompt.mimeTypes?.takeIf { it.size > 1 }?.let { intent.putExtra(Intent.EXTRA_MIME_TYPES, it) }
        pickFiles(intent) { uris ->
            result.complete(if (uris.isEmpty()) prompt.dismiss() else prompt.confirm(activity, uris.toTypedArray()))
        }
        return result
    }

    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        var answered = false
        dialog().setTitle("Quitter cette page ?").setMessage("Ce que tu as saisi peut être perdu.")
            .setPositiveButton("Quitter") { _, _ -> answered = true; result.complete(prompt.confirm(AllowOrDeny.ALLOW)) }
            .setNegativeButton("Rester") { _, _ -> answered = true; result.complete(prompt.confirm(AllowOrDeny.DENY)) }
            .setOnDismissListener { if (!answered) result.complete(prompt.confirm(AllowOrDeny.DENY)) }
            .show()
        return result
    }

    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        var answered = false
        dialog().setTitle("Renvoyer le formulaire ?").setMessage("Une commande ou un paiement pourrait être envoyé une deuxième fois.")
            .setPositiveButton("Renvoyer") { _, _ -> answered = true; result.complete(prompt.confirm(AllowOrDeny.ALLOW)) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> answered = true; result.complete(prompt.confirm(AllowOrDeny.DENY)) }
            .setOnDismissListener { if (!answered) result.complete(prompt.confirm(AllowOrDeny.DENY)) }
            .show()
        return result
    }

    // Pop-up windows open in the tab itself (onNewSession), never as windows.
    override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt): GeckoResult<PromptResponse> =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

    private fun padded(view: android.view.View) = LinearLayout(activity).apply {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)
        addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
}

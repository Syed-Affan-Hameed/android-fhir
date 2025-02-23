/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.fhir.datacapture.views

import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse

/**
 * Item for [QuestionnaireItemViewHolder] in [RecyclerView] containing
 * @param questionnaireItem [Questionnaire.QuestionnaireItemComponent](the question) and
 * [QuestionnaireResponse.Item](the answer).
 *
 * @param questionnaireResponseItem [Questionnaire.QuestionnaireItemComponent](the question) and
 * [QuestionnaireResponse.Item](the answer) are used to create the right type of view (e.g. a
 * CheckBox for a yes/no question) and populate the view with the right information (e.g text for
 * the CheckBox and initial yes/no answer for the CheckBox).
 *
 * @param questionnaireResponseItemChangedCallback function that should be called whenever the
 * `questionnaireResponseItemBuilder` is changed to inform the rest of the questionnaire to be
 * updated
 */
data class QuestionnaireItemViewItem(
  val questionnaireItem: Questionnaire.QuestionnaireItemComponent,
  val questionnaireResponseItem: QuestionnaireResponse.QuestionnaireResponseItemComponent,
  val resolveAnswerValueSet:
    suspend (String) -> List<Questionnaire.QuestionnaireItemAnswerOptionComponent> =
      {
    emptyList()
  },
  val questionnaireResponseItemChangedCallback: () -> Unit
) {
  /**
   * The single answer to the [QuestionnaireResponse.QuestionnaireResponseItemComponent], or `null`
   * if there is none or more than one answer.
   */
  var singleAnswerOrNull
    get() = questionnaireResponseItem.answer.singleOrNull()
    set(value) {
      questionnaireResponseItem.answer.clear()
      value?.let { questionnaireResponseItem.addAnswer(it) }
    }

  internal fun addAnswer(
    questionnaireResponseItemAnswerComponent:
      QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent
  ) {
    check(questionnaireItem.repeats) {
      "Questionnaire item with linkId ${questionnaireItem.linkId} does not allow repeated answers"
    }
    questionnaireResponseItem.answer.add(questionnaireResponseItemAnswerComponent)
  }

  internal fun removeAnswer(
    questionnaireResponseItemAnswerComponent:
      QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent
  ) {
    check(questionnaireItem.repeats) {
      "Questionnaire item with linkId ${questionnaireItem.linkId} does not allow repeated answers"
    }
    questionnaireResponseItem.answer.removeIf {
      it.value.equalsDeep(questionnaireResponseItemAnswerComponent.value)
    }
  }

  fun isAnswerOptionSelected(
    answerOption: Questionnaire.QuestionnaireItemAnswerOptionComponent
  ): Boolean {
    return questionnaireResponseItem.answer.any { it.value.equalsDeep(answerOption.value) }
  }

  /**
   * In a `choice` or `open-choice` type question, the answer options are defined in one of the two
   * elements in the questionnaire:
   *
   * - `Questionnaire.item.answerOption`: a list of permitted answers to the question
   * - `Questionnaire.item.answerValueSet`: a reference to a value set containing a list of
   * permitted answers to the question
   *
   * This property returns the answer options defined in one of the sources above. If the answer
   * options are defined in `Questionnaire.item.answerValueSet`, the answer value set will be
   * expanded.
   */
  internal val answerOption: List<Questionnaire.QuestionnaireItemAnswerOptionComponent>
    get() =
      runBlocking(Dispatchers.IO) {
        when {
          questionnaireItem.answerOption.isNotEmpty() -> questionnaireItem.answerOption
          !questionnaireItem.answerValueSet.isNullOrEmpty() ->
            resolveAnswerValueSet(questionnaireItem.answerValueSet)
          else -> emptyList()
        }
      }
}

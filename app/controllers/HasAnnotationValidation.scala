package controllers

import models.ContentType
import models.annotation.{ Annotation, AnnotationBody }
import models.contribution._
import models.annotation.AnnotationStatus

trait HasAnnotationValidation {

  private def createBodyContribution(annotationAfter: Annotation, createdBody: AnnotationBody) =
    Contribution(
      ContributionAction.CREATE_BODY,
      annotationAfter.lastModifiedBy.get,
      annotationAfter.lastModifiedAt,
      Item(
        ItemType.fromBodyType(createdBody.hasType),
        annotationAfter.annotates.documentId,
        Some(annotationAfter.annotates.filepartId),        
        annotationAfter.annotates.contentType,
        Some(annotationAfter.annotationId),
        Some(annotationAfter.versionId),
        None,
        // At least currently, bodies have either value or URI - never both
        if (createdBody.value.isDefined) createdBody.value else createdBody.uri
      ),
      Seq.empty[String]
    )

  /** Changes to bodies are either general 'edits' or status changes (confirmations or flags) **/
  private def determineChangeAction(bodyBefore: AnnotationBody, bodyAfter: AnnotationBody) =
    if (bodyAfter.uri == bodyBefore.uri && bodyAfter.status != bodyBefore.status)
      bodyAfter.status.get.value match {
        case AnnotationStatus.VERIFIED => ContributionAction.CONFIRM_BODY
        case AnnotationStatus.NOT_IDENTIFIABLE => ContributionAction.FLAG_BODY
      }
    else
      ContributionAction.EDIT_BODY
    
  private def changeBodyContribution(annotationBefore: Annotation, annotationAfter: Annotation, bodyBefore: AnnotationBody, bodyAfter: AnnotationBody) =
    Contribution(
      determineChangeAction(bodyBefore, bodyAfter),
      annotationAfter.lastModifiedBy.get,
      annotationAfter.lastModifiedAt,
      Item(
        ItemType.fromBodyType(bodyAfter.hasType),
        annotationAfter.annotates.documentId,
        Some(annotationAfter.annotates.filepartId),
        annotationAfter.annotates.contentType,
        Some(annotationAfter.annotationId),
        Some(annotationAfter.versionId),
        // At least currently, bodies have either value or URI - never both
        if (bodyBefore.value.isDefined) bodyBefore.value else bodyBefore.uri,
        if (bodyAfter.value.isDefined) bodyAfter.value else bodyAfter.uri
      ),
      if (bodyAfter.lastModifiedBy == bodyBefore.lastModifiedBy) Seq.empty[String] else Seq(bodyBefore.lastModifiedBy).flatten
    )
  
  private def deleteBodyContribution(annotationBefore: Annotation, annotationAfter: Annotation, deletedBody: AnnotationBody) =
    Contribution(
      ContributionAction.DELETE_BODY,
      annotationAfter.lastModifiedBy.get,
      annotationAfter.lastModifiedAt,
      Item(
        ItemType.fromBodyType(deletedBody.hasType),
        annotationAfter.annotates.documentId,
        Some(annotationAfter.annotates.filepartId),
        annotationAfter.annotates.contentType,       
        Some(annotationAfter.annotationId),
        Some(annotationAfter.versionId),
        // At least currently, bodies have either value or URI - never both
        if (deletedBody.value.isDefined) deletedBody.value else deletedBody.uri,
        None
      ),
      if (deletedBody.lastModifiedBy == annotationAfter.lastModifiedBy) Seq.empty[String] else Seq(deletedBody.lastModifiedBy).flatten
    )
  
  private def isPredecessorTo(before: AnnotationBody, after: AnnotationBody): Boolean =
    after.hasType == before.hasType
  
  def validateUpdate(annotation: Annotation, previousVersion: Option[Annotation]): Seq[Contribution] = {
    
    // TODO validation!
    // - make sure doc/filepart ID remains unchanged
    // - make sure filepart content type remains unchanged
    // - make sure annotation ID remains unchanged
      
    // TODO check any things the current user should not be able to manipulate
    // - createdAt/By info on bodies not touched by the user must be unchanged      
    
    computeContributions(annotation, previousVersion)
  }
  
  def computeContributions(annotation: Annotation, previousVersion: Option[Annotation]) = previousVersion match {
    
    case Some(before) => {
      // Body order never changes - so we compare before & after step by step 
      annotation.bodies.foldLeft((Seq.empty[Contribution], before.bodies)) { case ((contributions, referenceBodies), nextBodyAfter) =>
        // Leading bodies that are not predecessors to bodies in the new annotation are DELETIONS 
        val deletions = referenceBodies
          .takeWhile(bodyBefore => !isPredecessorTo(bodyBefore, nextBodyAfter))
          .map(deletedBody => deleteBodyContribution(before, annotation, deletedBody))
        
        // Once we're through detecting deletions, we continue with the remaining before-bodies
        val remainingReferenceBodies = referenceBodies.drop(deletions.size)       
        if (remainingReferenceBodies.isEmpty)
          // None left? Then this new body is an addition
          (contributions ++ deletions :+ createBodyContribution(annotation, nextBodyAfter),
            remainingReferenceBodies)
        else if (remainingReferenceBodies.head == nextBodyAfter)
          // This body is unchanged - continue with next
          (contributions ++ deletions,
            remainingReferenceBodies.tail)
        else
          // This body was updated 
          (contributions ++ deletions :+ changeBodyContribution(before, annotation, remainingReferenceBodies.head, nextBodyAfter),
            remainingReferenceBodies.tail)
      }._1 // We're not interested in the empty list of 'remaining reference bodies'
    }
   
    case None => {
      if (annotation.lastModifiedBy.isEmpty)
        // We don't count 'contributions' made automatic processes
        // TODO new annotation with no previous version - generate contributions
        Seq.empty[Contribution]
      else
        annotation.bodies.map(body => createBodyContribution(annotation, body))
    }

  }

}
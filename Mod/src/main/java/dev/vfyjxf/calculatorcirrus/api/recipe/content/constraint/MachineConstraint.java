package dev.vfyjxf.calculatorcirrus.api.recipe.content.constraint;

import dev.vfyjxf.calculatorcirrus.api.recipe.content.InputContent;

//TODO:如何描述机器的身份信息
public interface MachineConstraint {

    boolean satisfy(InputContent except, Object actual);

}

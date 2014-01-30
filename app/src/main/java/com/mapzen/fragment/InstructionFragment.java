package com.mapzen.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.mapzen.R;
import com.mapzen.osrm.Instruction;
import com.mapzen.util.DisplayHelper;
import com.mapzen.util.Logger;

public class InstructionFragment extends BaseFragment {

    private Instruction instruction;
    public static final int ROUTE_ZOOM_LEVEL = 17;
    private RouteFragment parent;
    private boolean hasPrev = false;
    private boolean hasNext = false;

    public void setHasPrev() {
        this.hasPrev = true;
    }

    public void setHasNext() {
        this.hasNext = true;
    }

    public void setParent(RouteFragment parent) {
        this.parent = parent;
    }

    public void setInstruction(Instruction instruction) {
        this.instruction = instruction;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_instruction, container, false);

        TextView fullInstruction = (TextView) view.findViewById(R.id.full_instruction);
        fullInstruction.setText(instruction.getFullInstruction());

        ImageView turnIcon = (ImageView) view.findViewById(R.id.turn_icon);
        turnIcon.setImageResource(DisplayHelper.getRouteDrawable(getActivity(),
                instruction.getTurnInstruction(), DisplayHelper.IconStyle.WHITE));

        if (hasNext) {
            ImageButton next = (ImageButton) view.findViewById(R.id.route_next);
            next.setVisibility(View.VISIBLE);
            next.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    parent.next();
                }
            });
        }

        if (hasPrev) {
            ImageButton prev = (ImageButton) view.findViewById(R.id.route_previous);
            prev.setVisibility(View.VISIBLE);
            prev.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    parent.prev();
                }
            });
        }

        return view;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            double[] point = instruction.getPoint();
            Logger.d("Instructions: " + instruction.toString());
            map.setMapPosition(point[0], point[1], Math.pow(2, ROUTE_ZOOM_LEVEL));
            map.getViewport().setRotation(instruction.getBearing());
        }
    }
}
